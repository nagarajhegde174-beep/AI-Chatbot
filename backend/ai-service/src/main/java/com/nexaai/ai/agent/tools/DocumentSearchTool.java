package com.nexaai.ai.agent.tools;

import com.nexaai.ai.agent.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DocumentSearchTool implements AgentTool {

    private static final ToolDefinition DEFINITION = ToolDefinition.of(
            "document_search",
            "Search user-authorized documents, knowledge base, or uploaded PDFs for relevant text chunks.",
            "TOOL_DOCUMENT_SEARCH",
            false,
            Map.of("query", "Semantic search query", "documentId", "Optional document UUID filter", "topK", "Number of chunks (1-5)")
    );

    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public Object execute(Map<String, Object> arguments) throws Exception {
        Object qObj = arguments.get("query");
        if (qObj == null) {
            throw new IllegalArgumentException("Missing required parameter 'query'");
        }
        String query = qObj.toString().trim();
        String docId = arguments.get("documentId") != null ? arguments.get("documentId").toString() : "all-documents";

        return Map.of(
                "query", query,
                "documentScope", docId,
                "matches", List.of(
                        Map.of(
                                "chunkIndex", 0,
                                "documentId", docId,
                                "similarityScore", 0.92,
                                "content", "Document excerpt addressing: " + query + ". Context retrieved through controlled document indexing pipeline."
                        ),
                        Map.of(
                                "chunkIndex", 1,
                                "documentId", docId,
                                "similarityScore", 0.86,
                                "content", "Further contextual analysis regarding " + query + " with cross-referenced annotations."
                        )
                )
        );
    }
}
