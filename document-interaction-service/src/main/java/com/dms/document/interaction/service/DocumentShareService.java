package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.ShareSettings;
import com.dms.document.interaction.dto.UpdateShareSettingsRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.model.DocumentInformation;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for managing document sharing operations
 */
public interface DocumentShareService {

    /**
     * Get sharing settings for a document
     *
     * @param documentId the ID of the document
     * @param username the username of the requesting user
     * @return the current share settings of the document
     */
    ShareSettings getDocumentShareSettings(String documentId, String username);

    /**
     * Update sharing settings for a document
     *
     * @param documentId the ID of the document
     * @param request the request containing updated sharing settings
     * @param username the username of the requesting user
     * @return the updated document information
     */
    DocumentInformation updateDocumentShareSettings(String documentId, UpdateShareSettingsRequest request, String username);

    /**
     * Search for users that can be shared with
     *
     * @param query the search query
     * @return list of matching users
     */
    List<UserResponse> searchShareableUsers(String query);

    /**
     * Get details for a list of users by their IDs
     *
     * @param userIds list of user IDs to retrieve
     * @return list of user responses with details
     */
    List<UserResponse> getShareableUserDetails(List<UUID> userIds);
}