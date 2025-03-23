package com.dms.processor.service;

import com.dms.processor.dto.NotificationEventRequest;
import com.dms.processor.model.DocumentInformation;

/**
 * Interface defining email notification operations related to documents
 * including notifications for document updates, comments, and reports.
 */
public interface DocumentEmailService {

    /**
     * Sends notification emails to users related to a document
     * when certain events occur (comments, updates, etc.)
     *
     * @param notificationEvent The notification event details
     */
    void sendNotifyForRelatedUserInDocument(NotificationEventRequest notificationEvent);

    /**
     * Sends emails to users who reported a document when the report is rejected
     *
     * @param document The document that was reported
     * @param rejecterId The ID of the user who rejected the report
     * @param times The number of times the document has been reported
     */
    void sendDocumentReportRejectionNotifications(DocumentInformation document, String rejecterId, int times);

    /**
     * Sends notifications when a document report is resolved
     *
     * @param document The document that was reported
     * @param resolverId The ID of the user who resolved the report
     * @param times The number of times the document has been reported
     */
    void sendResolveNotifications(DocumentInformation document, String resolverId, int times);

    /**
     * Sends notifications when a document report is remediated
     *
     * @param document The document that was reported
     * @param remediatorId The ID of the user who remediated the report
     */
    void sendReportRemediationNotifications(DocumentInformation document, String remediatorId);

    /**
     * Sends notifications related to comment report processing
     *
     * @param notificationEvent The notification event details
     */
    void sendCommentReportProcessNotification(NotificationEventRequest notificationEvent);
}