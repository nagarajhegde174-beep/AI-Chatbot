package com.nexaai.document.service;

import com.nexaai.document.exception.FileValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class FileValidationService {

    private final long maxFileSizeBytes;
    private final List<String> allowedContentTypes;

    public FileValidationService(
            @Value("${nexa.upload.max-file-size-bytes:26214400}") long maxFileSizeBytes,
            @Value("${nexa.upload.allowed-content-types:application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown,text/csv}") List<String> allowedContentTypes
    ) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.allowedContentTypes = allowedContentTypes;
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw FileValidationException.malformed("Uploaded file is empty or missing.");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw FileValidationException.tooLarge(maxFileSizeBytes);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw FileValidationException.malformed("Filename must not be empty.");
        }

        String extension = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
        String declaredContentType = file.getContentType();

        // Read initial header bytes (up to 512 bytes) for magic-byte sniffing
        byte[] headerBytes = readHeaderBytes(file, 512);

        // Security check: reject executable binaries immediately (ELF, MZ .exe)
        if (isExecutable(headerBytes)) {
            throw FileValidationException.contentMismatch("Executable binaries are strictly prohibited.");
        }

        switch (extension) {
            case "pdf" -> validatePdf(headerBytes);
            case "docx" -> validateDocx(headerBytes);
            case "txt" -> validatePlainText(headerBytes);
            case "md", "markdown" -> validateMarkdown(headerBytes);
            case "csv" -> validateCsv(headerBytes);
            default -> throw FileValidationException.unsupportedType("Extension ." + extension);
        }
    }

    private void validatePdf(byte[] header) {
        // PDF magic bytes: %PDF- (0x25, 0x50, 0x44, 0x46)
        if (header.length < 4 ||
                header[0] != 0x25 || header[1] != 0x50 || header[2] != 0x44 || header[3] != 0x46) {
            throw FileValidationException.contentMismatch("File extension is .pdf but content is not a valid PDF document.");
        }
    }

    private void validateDocx(byte[] header) {
        // DOCX is a zip package starting with PK\x03\x04 (0x50, 0x4B, 0x03, 0x04)
        if (header.length < 4 ||
                header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
            throw FileValidationException.contentMismatch("File extension is .docx but content is not a valid OpenXML document.");
        }
    }

    private void validatePlainText(byte[] header) {
        validateTextContent(header, "Plain text (.txt)");
    }

    private void validateMarkdown(byte[] header) {
        validateTextContent(header, "Markdown (.md)");
    }

    private void validateCsv(byte[] header) {
        validateTextContent(header, "CSV (.csv)");
    }

    private void validateTextContent(byte[] header, String typeName) {
        // Check for binary/null bytes which indicate non-text files
        for (byte b : header) {
            if (b == 0x00) {
                throw FileValidationException.contentMismatch(typeName + " contains binary null bytes.");
            }
        }
    }

    private boolean isExecutable(byte[] header) {
        if (header.length >= 2) {
            // Windows MZ header (0x4D, 0x5A)
            if (header[0] == 0x4D && header[1] == 0x5A) {
                return true;
            }
        }
        if (header.length >= 4) {
            // Linux ELF header (0x7F, 'E', 'L', 'F')
            if (header[0] == 0x7F && header[1] == 0x45 && header[2] == 0x4C && header[3] == 0x46) {
                return true;
            }
        }
        return false;
    }

    private byte[] readHeaderBytes(MultipartFile file, int maxBytes) {
        try (InputStream in = file.getInputStream()) {
            byte[] buffer = new byte[maxBytes];
            int read = in.read(buffer);
            if (read <= 0) {
                return new byte[0];
            }
            return Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            throw FileValidationException.malformed("Failed to read uploaded file: " + e.getMessage());
        }
    }

    public static String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }
}
