package com.nexaai.document.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private static final int DEFAULT_TARGET_CHUNK_SIZE = 800; // ~150-200 tokens
    private static final int DEFAULT_OVERLAP_SIZE = 120;     // ~25-30 tokens

    private final int targetChunkSize;
    private final int overlapSize;

    public ChunkingService() {
        this(DEFAULT_TARGET_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }

    public ChunkingService(int targetChunkSize, int overlapSize) {
        this.targetChunkSize = targetChunkSize;
        this.overlapSize = overlapSize;
    }

    /**
     * Splits text on structure first (paragraphs, headings) and then on sentences with overlap.
     */
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
            if (p.isEmpty()) {
                continue;
            }

            if (p.length() > targetChunkSize) {
                // If current buffer has content, flush it
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                }
                // Split long paragraph by sentences
                List<String> sentenceChunks = splitLongParagraph(p);
                chunks.addAll(sentenceChunks);
            } else if (currentChunk.length() + p.length() + 2 <= targetChunkSize) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(p);
            } else {
                // Flush current chunk
                String flushed = currentChunk.toString().trim();
                chunks.add(flushed);

                // Prepare next chunk with overlap from previous chunk
                currentChunk.setLength(0);
                String overlap = extractOverlap(flushed, overlapSize);
                if (!overlap.isEmpty()) {
                    currentChunk.append(overlap).append("\n\n");
                }
                currentChunk.append(p);
            }
        }

        if (currentChunk.length() > 0) {
            String remaining = currentChunk.toString().trim();
            if (!remaining.isEmpty()) {
                chunks.add(remaining);
            }
        }

        return chunks;
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> result = new ArrayList<>();
        // Split by sentence terminators
        String[] sentences = paragraph.split("(?<=[.!?])\\s+");
        StringBuilder buffer = new StringBuilder();

        for (String sentence : sentences) {
            String s = sentence.trim();
            if (s.isEmpty()) continue;

            if (buffer.length() + s.length() + 1 <= targetChunkSize) {
                if (buffer.length() > 0) buffer.append(" ");
                buffer.append(s);
            } else {
                if (buffer.length() > 0) {
                    result.add(buffer.toString().trim());
                    buffer.setLength(0);
                }
                if (s.length() > targetChunkSize) {
                    // Split hard if single sentence exceeds target size
                    result.addAll(hardSplit(s, targetChunkSize, overlapSize));
                } else {
                    buffer.append(s);
                }
            }
        }

        if (buffer.length() > 0) {
            result.add(buffer.toString().trim());
        }

        return result;
    }

    private List<String> hardSplit(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int length = text.length();

        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            chunks.add(text.substring(start, end).trim());
            if (end == length) break;
            start += (chunkSize - overlap);
        }
        return chunks;
    }

    private String extractOverlap(String text, int overlapLength) {
        if (text.length() <= overlapLength) {
            return text;
        }
        int splitPoint = text.length() - overlapLength;
        // Try to snap to the beginning of a word
        int spaceIndex = text.indexOf(' ', splitPoint);
        if (spaceIndex != -1 && spaceIndex < text.length() - 10) {
            return text.substring(spaceIndex + 1);
        }
        return text.substring(splitPoint);
    }
}
