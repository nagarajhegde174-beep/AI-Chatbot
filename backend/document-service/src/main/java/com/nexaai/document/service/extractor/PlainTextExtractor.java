package com.nexaai.document.service.extractor;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

@Component
public class PlainTextExtractor implements TextExtractor {

    private static final Set<String> EXTENSIONS = Set.of("txt", "md", "markdown");

    @Override
    public boolean supports(String extension) {
        return EXTENSIONS.contains(extension.toLowerCase());
    }

    @Override
    public String extractText(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        return content != null ? content.trim() : "";
    }
}
