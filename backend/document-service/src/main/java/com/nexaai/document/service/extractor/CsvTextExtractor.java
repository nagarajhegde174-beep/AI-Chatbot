package com.nexaai.document.service.extractor;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String extension) {
        return "csv".equalsIgnoreCase(extension);
    }

    @Override
    public String extractText(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return "";
            }

            List<String> headers = parseCsvLine(headerLine);
            sb.append("Columns: ").append(String.join(", ", headers)).append("\n\n");

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                sb.append("Row ").append(rowNum++).append(": ");
                for (int i = 0; i < values.size(); i++) {
                    String header = (i < headers.size()) ? headers.get(i) : "Col" + (i + 1);
                    sb.append(header).append("=").append(values.get(i));
                    if (i < values.size() - 1) {
                        sb.append(", ");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                result.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        result.add(cur.toString().trim());
        return result;
    }
}
