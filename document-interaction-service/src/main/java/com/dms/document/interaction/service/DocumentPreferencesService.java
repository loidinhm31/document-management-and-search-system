package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.UpdateDocumentPreferencesRequest;
import com.dms.document.interaction.enums.InteractionType;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentPreferences;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interface for managing document preferences and interactions
 */
public interface DocumentPreferencesService {

    /**
     * Get a user's document preferences
     *
     * @param username User's username
     * @return The user's document preferences
     */
    DocumentPreferences getDocumentPreferences(String username);

    /**
     * Update a user's explicit document preferences
     *
     * @param userId User ID
     * @param request Request containing preference updates
     * @return Updated document preferences
     */
    DocumentPreferences updateExplicitPreferences(String userId, UpdateDocumentPreferencesRequest request);

    /**
     * Record an interaction between a user and a document
     *
     * @param userId User ID
     * @param documentId Document ID
     * @param type Type of interaction
     */
    void recordInteraction(UUID userId, String documentId, InteractionType type);

    /**
     * Update implicit preferences based on user interactions
     *
     * @param userId User ID
     * @param document Document information
     * @param type Type of interaction
     */
    void updateImplicitPreferences(UUID userId, DocumentInformation document, InteractionType type);

    /**
     * Calculate content type weights for a user
     *
     * @param username User's username
     * @return Map of content types to weight values
     */
    Map<String, Double> getCalculateContentTypeWeights(String username);

    /**
     * Create default preferences for a new user
     *
     * @param userId User ID
     * @return Default document preferences
     */
    DocumentPreferences createDefaultPreferences(String userId);

    /**
     * Get tags recommended for a user based on their interactions
     *
     * @param userId User ID
     * @return Set of recommended tags
     */
    Set<String> getRecommendedTags(String userId);

    /**
     * Get statistics about a user's interactions
     *
     * @param username User's username
     * @return Map of interaction statistics
     */
    Map<String, Object> getInteractionStatistics(String username);
}