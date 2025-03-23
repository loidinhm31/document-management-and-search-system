package com.dms.processor.service;

import com.dms.processor.dto.DocumentExtractContent;

import java.nio.file.Path;
import java.util.Map;

/**
 * Service interface for extracting content from document files.
 */
public interface ContentExtractorService {

    /**
     * Extracts text content and metadata from a file.
     *
     * @param filePath the path to the file
     * @return DocumentExtractContent containing the extracted text and metadata
     */
    DocumentExtractContent extractContent(Path filePath);

}