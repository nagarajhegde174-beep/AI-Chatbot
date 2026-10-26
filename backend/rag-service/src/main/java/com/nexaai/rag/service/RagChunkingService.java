package com.nexaai.rag.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagChunkingService {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 120;

    public List<String> chunkText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.replace("\r\n", "\n").trim();
        String[] paragraphs = normalized.split("\n\\s*\n");

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            String p = paragraph.trim();
            if (p.isEmpty()) continue;

            if (p.length() > DEFAULT_CHUNK_SIZE) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                }
                chunks.addAll(splitParagraph(p));
            } else if (currentChunk.length() + p.length() + 2 <= DEFAULT_CHUNK_SIZE) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(p);
            } else {
                String flushed = currentChunk.toString().trim();
                chunks.add(flushed);
                currentChunk.setLength(0);
                String overlap = extractOverlap(flushed, DEFAULT_OVERLAP);
                if (!overlap.isEmpty()) {
                    currentChunk.append(overlap).append("\n\n");
                }
                currentChunk.append(p);
            }
        }

        if (currentChunk.length() > 0) {
            String rem = currentChunk.toString().trim();
            if (!rem.isEmpty()) chunks.add(rem);
        }

        return chunks;
    }

    private List<String> splitParagraph(String p) {
        List<String> result = new ArrayList<>();
        String[] sentences = p.split("(?<=[.!?])\\s+");
        StringBuilder buffer = new StringBuilder();

        for (String s : sentences) {
            String str = s.trim();
            if (str.isEmpty()) continue;

            if (buffer.length() + str.length() + 1 <= DEFAULT_CHUNK_SIZE) {
                if (buffer.length() > 0) buffer.append(" ");
                buffer.append(str);
            } else {
                if (buffer.length() > 0) {
                    result.add(buffer.toString().trim());
                    buffer.setLength(0);
                }
                buffer.append(str);
            }
        }
        if (buffer.length() > 0) {
            result.add(buffer.toString().trim());
        }
        return result;
    }

    private String extractOverlap(String text, int overlap) {
        if (text.length() <= overlap) return text;
        int idx = text.length() - overlap;
        int space = text.indexOf(' ', idx);
        if (space != -1 && space < text.length() - 10) {
            return text.substring(space + 1);
        }
        return text.substring(idx);
    }
}
