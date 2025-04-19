package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.DocumentFavoriteCheck;

/**
 * Service interface for managing document favorites.
 */
public interface DocumentFavoriteService {

    /**
     * Marks a document as favorite for a user.
     *
     * @param documentId The document ID to favorite
     * @param username The username of the user
     */
    void favoriteDocument(String documentId, String username);

    /**
     * Removes a document from a user's favorites.
     *
     * @param documentId The document ID to unfavorite
     * @param username The username of the user
     */
    void unfavoriteDocument(String documentId, String username);

    /**
     * Checks if a document is in a user's favorites, also return number of favorites for a specific document.
     *
     * @param documentId The document ID to check
     * @param username The username of the user
     * @return DocumentFavoriteCheck to check with True if the document is favorited by the user, False otherwise, also containing favorite count
     */
    DocumentFavoriteCheck checkDocumentFavorited(String documentId, String username);
}