package com.nexaai.document.service.extractor;

import com.nexaai.document.exception.DocumentException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class PdfTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    @Override
    public String extractText(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            if (document.isEncrypted()) {
                throw new DocumentException(
                        "PDF_PASSWORD_PROTECTED",
                        "This PDF is password protected and could not be read.",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text != null ? text.trim() : "";
        } catch (InvalidPasswordException e) {
            throw new DocumentException(
                    "PDF_PASSWORD_PROTECTED",
                    "This PDF is password protected and could not be read.",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }
}
