package com.dms.processor.service.extraction;

import com.dms.processor.exception.DocumentProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Factory for selecting and providing the appropriate document extraction strategy.
 * This factory manages the available strategies and selects the most appropriate one
 * based on file type and other characteristics.
 */
@Component
@Slf4j
public class DocumentExtractorFactory {

    private final List<DocumentExtractionStrategy> strategies;

    /**
     * Creates a new DocumentExtractorFactory with the available strategies.
     * The strategies are automatically ordered by their @Order annotation.
     */
    public DocumentExtractorFactory(List<DocumentExtractionStrategy> strategies) {
        this.strategies = strategies;
        log.info("Initialized DocumentExtractorFactory with {} strategies", strategies.size());

        // Log the available strategies for debugging
        if (log.isDebugEnabled()) {
            strategies.forEach(strategy ->
                    log.debug("Registered extraction strategy: {}", strategy.getClass().getSimpleName()));
        }
    }

    /**
     * Gets the appropriate extraction strategy for the given file and MIME type.
     * Strategies are checked in order of their priority (defined by @Order annotation).
     *
     * @param filePath The path to the document file
     * @param mimeType The MIME type of the document
     * @return The first strategy that can handle the document
     * @throws DocumentProcessingException if no suitable strategy is found
     */
    public DocumentExtractionStrategy getStrategy(Path filePath, String mimeType) {
        return strategies.stream()
                .filter(strategy -> strategy.canHandle(filePath, mimeType))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("No suitable extraction strategy found for MIME type: {}", mimeType);
                    return new DocumentProcessingException("No suitable extraction strategy found for MIME type: " + mimeType);
                });
    }
}