package com.nexaai.ai.agent.tools;

import com.nexaai.ai.agent.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FileAnalyzerTool implements AgentTool {

    private static final ToolDefinition DEFINITION = ToolDefinition.of(
            "file_analyzer",
            "Analyze file structure, text extract, word count, character count, and content summary.",
            "TOOL_FILE_ANALYZER",
            false,
            Map.of("content", "Raw text content or document text to analyze", "filename", "Optional filename")
    );

    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public Object execute(Map<String, Object> arguments) throws Exception {
        Object contentObj = arguments.get("content");
        if (contentObj == null) {
            contentObj = arguments.get("text");
        }
        if (contentObj == null) {
            throw new IllegalArgumentException("Missing required parameter 'content'");
        }
        String content = contentObj.toString();
        String filename = arguments.get("filename") != null ? arguments.get("filename").toString() : "document.txt";

        int charCount = content.length();
        String[] words = content.trim().isEmpty() ? new String[0] : content.trim().split("\\s+");
        int wordCount = words.length;
        int lineCount = content.split("\r\n|\r|\n").length;

        String fileType = "text/plain";
        if (filename.endsWith(".pdf")) fileType = "application/pdf";
        else if (filename.endsWith(".docx")) fileType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        else if (filename.endsWith(".json")) fileType = "application/json";
        else if (filename.endsWith(".md")) fileType = "text/markdown";

        return Map.of(
                "filename", filename,
                "mimeType", fileType,
                "characterCount", charCount,
                "wordCount", wordCount,
                "lineCount", lineCount,
                "hasCodeBlocks", content.contains("```"),
                "estimatedReadingTimeMinutes", Math.max(1, (int) Math.ceil(wordCount / 200.0))
        );
    }
}
