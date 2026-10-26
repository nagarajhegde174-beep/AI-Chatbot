package com.nexaai.document.service;

import com.nexaai.document.service.extractor.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextExtractionTest {

    @TempDir
    Path tempDir;

    private TextExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new TextExtractionService(List.of(
                new PlainTextExtractor(),
                new CsvTextExtractor(),
                new PdfTextExtractor(),
                new DocxTextExtractor()
        ));
    }

    @Test
    void testPlainTextExtraction() throws IOException {
        Path txtFile = tempDir.resolve("sample.txt");
        Files.writeString(txtFile, "This is plain text content.\nSecond line.");

        String text = extractionService.extractText(txtFile.toFile(), "txt");
        assertNotNull(text);
        assertTrue(text.contains("This is plain text content."));
        assertTrue(text.contains("Second line."));
    }

    @Test
    void testMarkdownExtraction() throws IOException {
        Path mdFile = tempDir.resolve("sample.md");
        Files.writeString(mdFile, "# Document Title\n\nThis is paragraph under header.");

        String text = extractionService.extractText(mdFile.toFile(), "md");
        assertNotNull(text);
        assertTrue(text.contains("# Document Title"));
        assertTrue(text.contains("This is paragraph under header."));
    }

    @Test
    void testCsvExtraction() throws IOException {
        Path csvFile = tempDir.resolve("data.csv");
        String csvContent = "Name,Department,Salary\nAlice,Engineering,100000\nBob,Design,85000";
        Files.writeString(csvFile, csvContent);

        String text = extractionService.extractText(csvFile.toFile(), "csv");
        assertNotNull(text);
        assertTrue(text.contains("Columns: Name, Department, Salary"));
        assertTrue(text.contains("Name=Alice, Department=Engineering, Salary=100000"));
        assertTrue(text.contains("Name=Bob, Department=Design, Salary=85000"));
    }

    @Test
    void testPdfExtraction() throws IOException {
        Path pdfPath = tempDir.resolve("test.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(100, 700);
                stream.showText("NexaAI Document Service PDF Extraction Test");
                stream.endText();
            }
            doc.save(pdfPath.toFile());
        }

        String text = extractionService.extractText(pdfPath.toFile(), "pdf");
        assertNotNull(text);
        assertTrue(text.contains("NexaAI Document Service PDF Extraction Test"));
    }

    @Test
    void testDocxExtraction() throws IOException {
        Path docxPath = tempDir.resolve("test.docx");
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(docxPath.toFile())) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("NexaAI DOCX Extraction Test Content");
            doc.write(fos);
        }

        String text = extractionService.extractText(docxPath.toFile(), "docx");
        assertNotNull(text);
        assertTrue(text.contains("NexaAI DOCX Extraction Test Content"));
    }
}
