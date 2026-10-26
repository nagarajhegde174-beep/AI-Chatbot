package com.nexaai.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.document.domain.Document;
import com.nexaai.document.domain.DocumentStatus;
import com.nexaai.document.dto.DocumentResponse;
import com.nexaai.document.repository.DocumentChunkRepository;
import com.nexaai.document.repository.DocumentRepository;
import com.nexaai.document.repository.DocumentStorageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DocumentServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentStorageRepository storageRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID user1 = UUID.randomUUID();
    private final UUID user2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        chunkRepository.deleteAll();
        storageRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    void testUploadAndRetrieveFlow() throws Exception {
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            contentBuilder.append("Paragraph ").append(i)
                    .append(" contains substantial informative text detailing the operations and policies of the document service in NexaAI.\n\n");
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-notes.txt",
                "text/plain",
                contentBuilder.toString().getBytes()
        );

        // 1. Upload
        String uploadResponseJson = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header("X-User-Id", user1.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").exists())
                .andExpect(jsonPath("$.title").value("test-notes.txt"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(org.hamcrest.Matchers.greaterThan(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        DocumentResponse docResponse = objectMapper.readValue(uploadResponseJson, DocumentResponse.class);
        UUID documentId = docResponse.documentId();
        int expectedChunkCount = docResponse.chunkCount();

        // 2. Get Metadata
        mockMvc.perform(get("/api/v1/documents/" + documentId)
                        .header("X-User-Id", user1.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.status").value("READY"));

        // 3. Get Content (chunks)
        mockMvc.perform(get("/api/v1/documents/" + documentId + "/content")
                        .header("X-User-Id", user1.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.chunkCount").value(expectedChunkCount))
                .andExpect(jsonPath("$.chunks[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.chunks[0].text").value(org.hamcrest.Matchers.containsString("Paragraph 1")));

        // 4. Download file
        mockMvc.perform(get("/api/v1/documents/" + documentId + "/download")
                        .header("X-User-Id", user1.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paragraph 1")));

        // 5. Reprocess
        mockMvc.perform(post("/api/v1/documents/" + documentId + "/reprocess")
                        .header("X-User-Id", user1.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(expectedChunkCount));

        // 6. Delete
        mockMvc.perform(delete("/api/v1/documents/" + documentId)
                        .header("X-User-Id", user1.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isNoContent());

        // Verify deleted
        assertFalse(documentRepository.existsById(documentId));
        assertEquals(0, chunkRepository.countByDocumentId(documentId));
    }

    @Test
    void testTenantOwnershipIsolation() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "private.txt",
                "text/plain",
                "Private data belonging to User 1".getBytes()
        );

        String uploadResponseJson = mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header("X-User-Id", user1.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        DocumentResponse docResponse = objectMapper.readValue(uploadResponseJson, DocumentResponse.class);
        UUID docId = docResponse.documentId();

        // User 2 trying to get metadata -> 404
        mockMvc.perform(get("/api/v1/documents/" + docId)
                        .header("X-User-Id", user2.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isNotFound());

        // User 2 trying to get content -> 404
        mockMvc.perform(get("/api/v1/documents/" + docId + "/content")
                        .header("X-User-Id", user2.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isNotFound());

        // User 2 trying to download -> 404
        mockMvc.perform(get("/api/v1/documents/" + docId + "/download")
                        .header("X-User-Id", user2.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isNotFound());

        // User 2 trying to delete -> 404
        mockMvc.perform(delete("/api/v1/documents/" + docId)
                        .header("X-User-Id", user2.toString())
                        .header("X-User-Roles", "USER"))
                .andExpect(status().isNotFound());

        // Document must still exist for User 1
        assertTrue(documentRepository.existsById(docId));
    }

    @Test
    void testInternalServiceInfo() throws Exception {
        mockMvc.perform(get("/internal/v1/document/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("document-service"))
                .andExpect(jsonPath("$.port").value(8085))
                .andExpect(jsonPath("$.database").value("nexa_document"));
    }
}
