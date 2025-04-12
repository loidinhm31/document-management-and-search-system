package com.dms.processor.service;

import com.dms.processor.model.DocumentContent;

import java.util.Map;
import java.util.Optional;

public interface DocumentContentService {

    /**
     * Saves content for a specific version of a document
     *
     * @param documentId Unique identifier of the document
     * @param versionNumber Version number of the document
     * @param content Content of the document
     * @param metadata Additional metadata associated with the document
     */
    void saveVersionContent(String documentId, Integer versionNumber,
                            String content, Map<String, String> metadata);

    /**
     * Retrieves content for a specific version of a document
     *
     * @param documentId Unique identifier of the document
     * @param versionNumber Version number of the document
     * @return Optional containing the document content if found, empty otherwise
     */
    Optional<DocumentContent> getVersionContent(String documentId, Integer versionNumber);
}