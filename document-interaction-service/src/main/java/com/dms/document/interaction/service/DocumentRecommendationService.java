package com.dms.document.interaction.service;

/**
 * Service interface for document recommendation operations.
 */
public interface DocumentRecommendationService {

    /**
     * Recommends a document for a user.
     *
     * @param documentId the ID of the document to recommend
     * @param username the username of the user making the recommendation
     * @return true if recommendation was successful, false if already recommended
     */
    boolean recommendDocument(String documentId, String username);

    /**
     * Removes a recommendation for a document from a user.
     *
     * @param documentId the ID of the document to unrecommend
     * @param username the username of the user removing the recommendation
     * @return true if unrecommendation was successful, false if not recommended
     */
    boolean unrecommendDocument(String documentId, String username);

    /**
     * Checks if a document is recommended by a specific user.
     *
     * @param documentId the ID of the document to check
     * @param username the username of the user to check
     * @return true if the document is recommended by the user, false otherwise
     */
    boolean isDocumentRecommendedByUser(String documentId, String username);
}