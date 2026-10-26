package com.nexaai.document.service.extractor;

import java.io.File;
import java.io.IOException;

public interface TextExtractor {

    boolean supports(String extension);

    String extractText(File file) throws IOException;
}
