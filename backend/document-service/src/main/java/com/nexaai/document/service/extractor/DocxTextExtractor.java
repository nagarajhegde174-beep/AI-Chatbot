package com.nexaai.document.service.extractor;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Component
public class DocxTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String extension) {
        return "docx".equalsIgnoreCase(extension);
    }

    @Override
    public String extractText(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            return text != null ? text.trim() : "";
        }
    }
}
