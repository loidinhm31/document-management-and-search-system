package com.sdms.document.service;

import com.sdms.document.model.DocumentContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ContentExtractor {
    private final Tika tika;
    private static final int MAX_TEXT_LENGTH = 100_000; // Adjust based on your needs

    public ContentExtractor() {
        this.tika = new Tika();
        // Set maximum text length to avoid memory issues
        this.tika.setMaxStringLength(MAX_TEXT_LENGTH);
    }

    public DocumentContent extractContent(Path filePath) {
        try {
            // Read file metadata
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            // Extract content with metadata
            String content = tika.parseToString(Files.newInputStream(filePath), metadata);

            return DocumentContent.builder()
                    .content(content)
                    .metadata(extractMetadata(metadata))
                    .build();

        } catch (Exception e) {
            log.error("Error extracting content from file: {}", filePath, e);
            return DocumentContent.builder()
                    .content("")
                    .metadata(new HashMap<>())
                    .build();
        }
    }

    private Map<String, String> extractMetadata(Metadata metadata) {
        Map<String, String> metadataMap = new HashMap<>();
        for (String name : metadata.names()) {
            metadataMap.put(name, metadata.get(name));
        }
        return metadataMap;
    }
}