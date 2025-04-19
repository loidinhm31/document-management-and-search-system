package com.dms.document.interaction.service;

/**
 * Service interface for document recommendation operations.
 */
public interface DocumentRecommendationService {

    /**
     * Recommends/ Removes a recommendation for a document for a user.
     *
     * @param documentId the ID of the document to recommend
     * @param recommend true to recommend, false to remove recommendation
     * @param username the username of the user making/ removing the recommendation
     * @return true if the recommendation was successful, false if already recommended
     */
    boolean recommendDocument(String documentId, boolean recommend, String username);

    /**
     * Checks if a document is recommended by a specific user.
     *
     * @param documentId the ID of the document to check
     * @param username the username of the user to check
     * @return true if the document is recommended by the user, false otherwise
     */
    boolean isDocumentRecommendedByUser(String documentId, String username);
}