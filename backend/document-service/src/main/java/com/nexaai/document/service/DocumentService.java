package com.nexaai.document.service;

import com.nexaai.document.domain.Document;
import com.nexaai.document.domain.DocumentChunk;
import com.nexaai.document.domain.DocumentStatus;
import com.nexaai.document.domain.DocumentStorage;
import com.nexaai.document.dto.DocumentContentResponse;
import com.nexaai.document.dto.DocumentResponse;
import com.nexaai.document.dto.DocumentChunkResponse;
import com.nexaai.document.dto.event.DocumentChunkedPayload;
import com.nexaai.document.exception.DocumentException;
import com.nexaai.document.exception.DocumentNotFoundException;
import com.nexaai.document.repository.DocumentChunkRepository;
import com.nexaai.document.repository.DocumentRepository;
import com.nexaai.document.repository.DocumentStorageRepository;
import com.nexaai.document.service.extractor.TextExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentStorageRepository storageRepository;
    private final DocumentChunkRepository chunkRepository;
    private final FileValidationService validationService;
    private final StorageService storageService;
    private final TextExtractionService extractionService;
    private final ChunkingService chunkingService;
    private final DocumentEventPublisher eventPublisher;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentStorageRepository storageRepository,
            DocumentChunkRepository chunkRepository,
            FileValidationService validationService,
            StorageService storageService,
            TextExtractionService extractionService,
            ChunkingService chunkingService,
            DocumentEventPublisher eventPublisher
    ) {
        this.documentRepository = documentRepository;
        this.storageRepository = storageRepository;
        this.chunkRepository = chunkRepository;
        this.validationService = validationService;
        this.storageService = storageService;
        this.extractionService = extractionService;
        this.chunkingService = chunkingService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Upload, validate, store, extract text, chunk and publish events.
     */
    public DocumentResponse uploadDocument(UUID userId, MultipartFile file, String correlationId) {
        // 1. Validate
        validationService.validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long sizeBytes = file.getSize();
        String extension = FileValidationService.getFileExtension(originalFilename);

        UUID docId = UUID.randomUUID();
        Document document = new Document(
                docId,
                userId,
                originalFilename,
                originalFilename,
                contentType != null ? contentType : "application/octet-stream",
                sizeBytes
        );
        document.setStatus(DocumentStatus.UPLOADING);
        documentRepository.save(document);

        // 2. Store on disk outside web root with random name
        StorageService.StorageResult storageResult;
        try {
            storageResult = storageService.store(file);
        } catch (IOException e) {
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason("Failed to store file: " + e.getMessage());
            documentRepository.save(document);
            eventPublisher.publishDocumentFailed(docId, userId, "STORAGE_ERROR", document.getFailureReason(), correlationId);
            throw new DocumentException("STORAGE_ERROR", "Failed to store uploaded file.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        DocumentStorage storage = new DocumentStorage(
                UUID.randomUUID(),
                document,
                storageResult.storagePath(),
                storageResult.checksumSha256(),
                storageResult.storedBytes()
        );
        storageRepository.save(storage);

        // Emit DOCUMENT_READY / document.uploaded.v1
        eventPublisher.publishDocumentUploaded(docId, userId, originalFilename, contentType, sizeBytes, storageResult.storagePath(), correlationId);

        // 3. Process: Extract text and chunk
        processDocument(document, storage, extension, correlationId);

        return DocumentResponse.from(document);
    }

    /**
     * Extract text, generate chunks, update document status, and emit document.chunked.v1
     */
    public void processDocument(Document document, DocumentStorage storage, String extension, String correlationId) {
        document.setStatus(DocumentStatus.PROCESSING);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);

        File file = storageService.getFile(storage.getStoragePath());

        try {
            String extractedText = extractionService.extractText(file, extension);
            if (extractedText == null || extractedText.isBlank()) {
                document.setStatus(DocumentStatus.FAILED);
                document.setFailureReason("No readable text could be extracted from this document.");
                document.setUpdatedAt(Instant.now());
                documentRepository.save(document);
                eventPublisher.publishDocumentFailed(document.getId(), document.getUserId(), "EXTRACTION_EMPTY", document.getFailureReason(), correlationId);
                return;
            }

            // Chunk the extracted text
            List<String> rawChunks = chunkingService.chunkText(extractedText);
            chunkRepository.deleteByDocumentId(document.getId());

            List<DocumentChunkedPayload.ChunkItem> chunkItems = new ArrayList<>();
            for (int i = 0; i < rawChunks.size(); i++) {
                String chunkText = rawChunks.get(i);
                DocumentChunk chunk = new DocumentChunk(UUID.randomUUID(), document, i, chunkText);
                chunkRepository.save(chunk);
                chunkItems.add(new DocumentChunkedPayload.ChunkItem(i, chunkText));
            }

            document.setChunkCount(rawChunks.size());
            document.setStatus(DocumentStatus.READY);
            document.setFailureReason(null);
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);

            // Publish document.chunked.v1 for RAG service to embed & index
            eventPublisher.publishDocumentChunked(document.getId(), document.getUserId(), document.getTitle(), chunkItems, correlationId);
            log.info("Document {} successfully processed and chunked ({} chunks).", document.getId(), rawChunks.size());

        } catch (DocumentException e) {
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason(e.getMessage());
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);
            eventPublisher.publishDocumentFailed(document.getId(), document.getUserId(), e.getCode(), e.getMessage(), correlationId);
            log.warn("Document {} processing failed: {}", document.getId(), e.getMessage());
        } catch (Exception e) {
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason("Failed to extract text: " + e.getMessage());
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);
            eventPublisher.publishDocumentFailed(document.getId(), document.getUserId(), "EXTRACTION_ERROR", document.getFailureReason(), correlationId);
            log.error("Document {} processing unexpected error: {}", document.getId(), e.getMessage(), e);
        }
    }

    /**
     * Reprocess an existing document: re-extract text, re-chunk and republish events.
     */
    public DocumentResponse reprocessDocument(UUID documentId, UUID userId, boolean isAdmin, String correlationId) {
        Document document = findDocumentOrThrow(documentId, userId, isAdmin);
        DocumentStorage storage = storageRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new DocumentException("STORAGE_NOT_FOUND", "Document storage record missing.", HttpStatus.NOT_FOUND));

        String extension = FileValidationService.getFileExtension(document.getOriginalFilename());
        processDocument(document, storage, extension, correlationId);

        return DocumentResponse.from(document);
    }

    /**
     * Get document metadata with ownership check.
     */
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId, UUID userId, boolean isAdmin) {
        Document document = findDocumentOrThrow(documentId, userId, isAdmin);
        return DocumentResponse.from(document);
    }

    /**
     * List user documents (document library) with pagination and optional search filter.
     */
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listDocuments(UUID userId, String query, Pageable pageable) {
        Page<Document> docs;
        if (query != null && !query.isBlank()) {
            docs = documentRepository.findByUserIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(userId, query.trim(), pageable);
        } else {
            docs = documentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return docs.map(DocumentResponse::from);
    }

    /**
     * Retrieve extracted text chunks (content endpoint).
     */
    @Transactional(readOnly = true)
    public DocumentContentResponse getDocumentContent(UUID documentId, UUID userId, boolean isAdmin) {
        Document document = findDocumentOrThrow(documentId, userId, isAdmin);
        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<DocumentChunkResponse> chunkResponses = chunks.stream()
                .map(DocumentChunkResponse::from)
                .collect(Collectors.toList());

        return new DocumentContentResponse(
                document.getId(),
                document.getTitle(),
                document.getChunkCount(),
                chunkResponses,
                Instant.now()
        );
    }

    /**
     * Download stored file with ownership check.
     */
    @Transactional(readOnly = true)
    public StoredFileDownload getDocumentForDownload(UUID documentId, UUID userId, boolean isAdmin) throws IOException {
        Document document = findDocumentOrThrow(documentId, userId, isAdmin);
        DocumentStorage storage = storageRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        Resource resource = storageService.loadAsResource(storage.getStoragePath());
        return new StoredFileDownload(document.getOriginalFilename(), document.getContentType(), storage.getStoredBytes(), resource);
    }

    /**
     * Delete document, stored file, chunks, and emit document.deleted.v1.
     */
    public void deleteDocument(UUID documentId, UUID userId, boolean isAdmin, String correlationId) {
        Document document = findDocumentOrThrow(documentId, userId, isAdmin);

        storageRepository.findByDocumentId(documentId).ifPresent(storage -> {
            storageService.delete(storage.getStoragePath());
            storageRepository.delete(storage);
        });

        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);

        eventPublisher.publishDocumentDeleted(documentId, document.getUserId(), correlationId);
        log.info("Document {} deleted by user {}", documentId, userId);
    }

    private Document findDocumentOrThrow(UUID documentId, UUID userId, boolean isAdmin) {
        if (isAdmin) {
            return documentRepository.findById(documentId)
                    .orElseThrow(() -> new DocumentNotFoundException(documentId));
        }
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    public record StoredFileDownload(
            String originalFilename,
            String contentType,
            long sizeBytes,
            Resource resource
    ) {}
}
