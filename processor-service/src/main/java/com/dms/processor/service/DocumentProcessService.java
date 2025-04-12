package com.dms.processor.service;

import com.dms.processor.enums.EventType;
import com.dms.processor.model.DocumentInformation;

/**
 * Service interface for document processing operations
 */
public interface DocumentProcessService {

    /**
     * Process a document based on the event type
     *
     * @param document The document information to process
     * @param versionNumber The version number of the document
     * @param eventType The type of event triggering the processing
     */
    void processDocument(DocumentInformation document, Integer versionNumber, EventType eventType);

    /**
     * Handle document report status changes
     *
     * @param documentId The ID of the document
     * @param userId The ID of the user who changed the status
     * @param times The number of times the report has been processed
     */
    void handleReportStatus(String documentId, String userId, int times);

    /**
     * Delete a document from the search index
     *
     * @param documentId The ID of the document to delete
     */
    void deleteDocumentFromIndex(String documentId);
}