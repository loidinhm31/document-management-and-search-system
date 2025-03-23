package com.dms.document.interaction.service;

import com.dms.document.interaction.model.DocumentInformation;

import java.util.UUID;

/**
 * Service interface for handling document notifications.
 */
public interface DocumentNotificationService {

    /**
     * Handles notification for a new comment.
     *
     * @param document The document information
     * @param username The username of the commenter
     * @param userId The user ID of the commenter
     * @param newCommentId The ID of the new comment
     */
    void handleCommentNotification(DocumentInformation document, String username, UUID userId, Long newCommentId);

    /**
     * Handles notification for a new file version.
     *
     * @param document The document information
     * @param username The username of the user who updated the file
     * @param versionNumber The version number
     */
    void handleFileVersionNotification(DocumentInformation document, String username, Integer versionNumber);

    /**
     * Handles notification for a document revert.
     *
     * @param document The document information
     * @param username The username of the user who reverted the document
     * @param versionNumber The version number
     */
    void handleRevertNotification(DocumentInformation document, String username, Integer versionNumber);

    /**
     * Sends notification for a resolved comment report.
     *
     * @param documentId The document ID
     * @param commentId The comment ID
     * @param adminId The user ID of the admin who resolved the report
     * @param times The number of times the comment was reported
     */
    void sendCommentReportResolvedNotification(String documentId, Long commentId, UUID adminId, int times);
}