package com.dms.processor.service.extraction;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.exception.DocumentProcessingException;

import java.nio.file.Path;
import java.util.Map;

/**
 * Interface defining a document extraction strategy.
 * Each implementation handles a specific document type or extraction method.
 */
public interface DocumentExtractionStrategy {

    /**
     * Checks if this strategy can handle the given file with specified MIME type.
     *
     * @param filePath The path to the document file
     * @param mimeType The MIME type of the document
     * @return true if this strategy can handle the document, false otherwise
     */
    boolean canHandle(Path filePath, String mimeType);

    /**
     * Extracts content from the document.
     *
     * @param filePath The path to the document file
     * @param mimeType The MIME type of the document
     * @return A DocumentExtractContent containing the extracted text and metadata
     * @throws DocumentProcessingException If there is an error during extraction
     */
    DocumentExtractContent extract(Path filePath, String mimeType, Map<String, String> metadata) throws DocumentProcessingException;
}