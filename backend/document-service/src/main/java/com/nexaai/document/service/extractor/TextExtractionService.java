package com.nexaai.document.service.extractor;

import com.nexaai.document.exception.FileValidationException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Service
public class TextExtractionService {

    private final List<TextExtractor> extractors;

    public TextExtractionService(List<TextExtractor> extractors) {
        this.extractors = extractors;
    }

    public String extractText(File file, String extension) throws IOException {
        for (TextExtractor extractor : extractors) {
            if (extractor.supports(extension)) {
                return extractor.extractText(file);
            }
        }
        throw FileValidationException.unsupportedType(extension);
    }
}
