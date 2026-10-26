package com.nexaai.document.service;

import com.nexaai.document.exception.FileValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileValidationTest {

    private FileValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new FileValidationService(
                1024 * 1024, // 1MB limit
                List.of("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "text/markdown", "text/csv")
        );
    }

    @Test
    void testValidPdfValidation() {
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf", "application/pdf", pdfBytes);

        assertDoesNotThrow(() -> validationService.validateFile(file));
    }

    @Test
    void testInvalidPdfMagicBytes() {
        byte[] fakePdfBytes = "This is not a pdf file!".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", fakePdfBytes);

        FileValidationException ex = assertThrows(FileValidationException.class, () -> validationService.validateFile(file));
        assertEquals("DOCUMENT_CONTENT_MISMATCH", ex.getCode());
    }

    @Test
    void testValidDocxMagicBytes() {
        byte[] docxHeader = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "doc.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxHeader);

        assertDoesNotThrow(() -> validationService.validateFile(file));
    }

    @Test
    void testInvalidDocxMagicBytes() {
        byte[] fakeDocx = "Plain text disguised as docx".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", fakeDocx);

        FileValidationException ex = assertThrows(FileValidationException.class, () -> validationService.validateFile(file));
        assertEquals("DOCUMENT_CONTENT_MISMATCH", ex.getCode());
    }

    @Test
    void testValidPlainText() {
        byte[] txtBytes = "Hello, world! This is valid plain text.\nSecond line.".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", txtBytes);

        assertDoesNotThrow(() -> validationService.validateFile(file));
    }

    @Test
    void testPlainTextWithNullBytesRejected() {
        byte[] binaryBytes = new byte[]{0x48, 0x65, 0x6C, 0x00, 0x6F};
        MockMultipartFile file = new MockMultipartFile("file", "binary.txt", "text/plain", binaryBytes);

        FileValidationException ex = assertThrows(FileValidationException.class, () -> validationService.validateFile(file));
        assertEquals("DOCUMENT_CONTENT_MISMATCH", ex.getCode());
    }

    @Test
    void testExecutableBinaryRejected() {
        // Windows MZ header (0x4D, 0x5A)
        byte[] exeBytes = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "malicious.pdf", "application/pdf", exeBytes);

        FileValidationException ex = assertThrows(FileValidationException.class, () -> validationService.validateFile(file));
        assertEquals("DOCUMENT_CONTENT_MISMATCH", ex.getCode());
        assertTrue(ex.getMessage().contains("Executable binaries are strictly prohibited"));
    }

    @Test
    void testOversizeFileRejected() {
        byte[] largeBytes = new byte[2 * 1024 * 1024]; // 2MB > 1MB limit
        largeBytes[0] = '%'; largeBytes[1] = 'P'; largeBytes[2] = 'D'; largeBytes[3] = 'F'; largeBytes[4] = '-';
        MockMultipartFile file = new MockMultipartFile("file", "large.pdf", "application/pdf", largeBytes);

        FileValidationException ex = assertThrows(FileValidationException.class, () -> validationService.validateFile(file));
        assertEquals("DOCUMENT_TOO_LARGE", ex.getCode());
    }

    @Test
    void testUnsupportedExtensionRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "script.py", "text/x-python", "print('hello')".getBytes());

        FileValidationException ex = assertThrows(FileValidationException.class, () -> validationService.validateFile(file));
        assertEquals("DOCUMENT_UNSUPPORTED_TYPE", ex.getCode());
    }
}
