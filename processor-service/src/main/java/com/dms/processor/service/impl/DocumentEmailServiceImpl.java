package com.dms.processor.service.impl;

import com.dms.processor.dto.NotificationEventRequest;
import com.dms.processor.enums.CommentReportStatus;
import com.dms.processor.enums.DocumentReportStatus;
import com.dms.processor.enums.NotificationType;
import com.dms.processor.model.DocumentComment;
import com.dms.processor.model.DocumentInformation;
import com.dms.processor.model.User;
import com.dms.processor.repository.*;
import com.dms.processor.service.DocumentEmailService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentEmailServiceImpl extends EmailService implements DocumentEmailService {
    @Value("${app.base-url}")
    private String baseUrl;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentFavoriteRepository documentFavoriteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentReportRepository documentReportRepository;

    @Autowired
    private CommentReportRepository commentReportRepository;

    @Autowired
    private DocumentCommentRepository documentCommentRepository;


    protected class NotificationContext {
        final Map<String, Object> variables = new HashMap<>();

        NotificationContext() {
            variables.put("baseUrl", baseUrl);
        }

        NotificationContext withDocument(DocumentInformation document) {
            variables.put("documentId", document.getId());
            variables.put("documentTitle", document.getFilename());
            return this;
        }

        NotificationContext withActionUser(String username) {
            variables.put("actionUser", username);
            return this;
        }

        NotificationContext withTriggerUser(String username) {
            variables.put("triggerUser", username);
            return this;
        }

        NotificationContext withResolverUser(String username) {
            variables.put("resolverUser", username);
            return this;
        }

        NotificationContext withRecipientMap(Map<String, User> recipients) {
            variables.put("recipientMap", recipients);
            return this;
        }

        NotificationContext withVersionNumber(Integer versionNumber) {
            variables.put("versionNumber", versionNumber);
            return this;
        }

        NotificationContext withRevertedVersion(Integer versionNumber) {
            variables.put("revertedToVersion", versionNumber);
            return this;
        }

        NotificationContext withActionType(String actionType) {
            variables.put("actionType", actionType);
            return this;
        }

        NotificationContext withCommentId(Long commentId) {
            variables.put("commentId", commentId);
            return this;
        }

        NotificationContext withCommentContent(String content) {
            variables.put("commentContent", content);
            return this;
        }

        NotificationContext withRecipientName(String name) {
            variables.put("recipientName", name);
            return this;
        }

        Map<String, Object> getVariables() {
            return variables;
        }
    }

    @Override
    public void sendNotifyForRelatedUserInDocument(NotificationEventRequest notificationEvent) {
        DocumentInformation document = findDocument(notificationEvent.getDocumentId());
        User triggerUser = findUserByUsername(notificationEvent.getTriggerUserId());
        Set<User> usersToNotify = findUsersToNotify(document, triggerUser);

        if (CollectionUtils.isNotEmpty(usersToNotify)) {
            sendNotifications(usersToNotify, triggerUser, document, notificationEvent);
            log.info("Sent notification emails to {} users for document: {}",
                    usersToNotify.size(), document.getId());
        } else {
            log.info("No recipients found for notification event {}", notificationEvent.getEventId());
        }
    }

    @Override
    public void sendDocumentReportRejectionNotifications(DocumentInformation document, String rejecterId, int times) {
        String rejectedByUsername = getUsernameById(rejecterId);
        Set<UUID> reporterUserIds = documentReportRepository.findReporterUserIdsByDocumentIdAndStatusAndTimes(
                document.getId(), DocumentReportStatus.REJECTED, times);

        if (CollectionUtils.isNotEmpty(reporterUserIds)) {
            Set<User> reporters = new HashSet<>(userRepository.findUsersByUserIdIn(reporterUserIds));

            sendReportStatusEmails(reporters, document, rejectedByUsername,
                    String.format("Update on your report for document: %s", document.getFilename()),
                    "document-report-rejected-reporter-notification");

            log.info("Sent report rejection notification emails to {} reporters for document: {}",
                    reporters.size(), document.getId());
        } else {
            log.info("No reporters found for document: {}", document.getId());
        }
    }

    @Override
    public void sendResolveNotifications(DocumentInformation document, String resolverId, int times) {
        String resolvedByUsername = getUsernameById(resolverId);
        User creator = findUserById(document.getUserId());
        Set<User> favoriters = findFavoriters(document);

        Set<UUID> reporterUserIds = documentReportRepository.findReporterUserIdsByDocumentIdAndStatusAndTimes(
                document.getId(), DocumentReportStatus.RESOLVED, times);

        // Notify creator
        if (Objects.nonNull(creator)) {
            sendMailToCreator("Your document has been removed",
                    "document-report-resolved-creator-notification",
                    creator, document, resolvedByUsername);
            log.info("Sent report resolved notification to creator for document: {}", document.getId());
        } else {
            log.info("No creator found for report notification for document: {}", document.getId());
        }

        // Notify reporters
        if (CollectionUtils.isNotEmpty(reporterUserIds)) {
            Set<User> reporters = new HashSet<>(userRepository.findUsersByUserIdIn(reporterUserIds));

            sendReportStatusEmails(reporters, document, resolvedByUsername,
                    String.format("Report resolved for document: %s", document.getFilename()),
                    "document-report-resolved-reporter-notification");
            log.info("Sent report resolved notifications to {} reporters for document: {}",
                    reporters.size(), document.getId());
        } else {
            log.info("No reporters found for report notification for document: {}", document.getId());
        }

        // Notify favoriters
        if (CollectionUtils.isNotEmpty(favoriters)) {
            sendReportStatusEmails(favoriters, document, resolvedByUsername,
                    String.format("Favorite document removed: %s No Longer Available", document.getFilename()),
                    "document-report-resolved-favoriter-notification");
            log.info("Sent report removed notifications to {} favoriters for document: {}",
                    favoriters.size(), document.getId());
        } else {
            log.info("No favoriter found for report notification for document: {}", document.getId());
        }
    }

    @Override
    public void sendReportRemediationNotifications(DocumentInformation document, String remediatorId) {
        String remediatedByUsername = getUsernameById(remediatorId);
        User creator = findUserById(document.getUserId());
        Set<User> favoriters = findFavoriters(document);

        // Notify creator
        if (Objects.nonNull(creator)) {
            sendMailToCreator("Your document is now available",
                    "document-report-remediated-creator-notification",
                    creator, document, remediatedByUsername);
            log.info("Sent report remediation notification to creator for document: {}", document.getId());
        }

        // Notify favoriters
        if (CollectionUtils.isNotEmpty(favoriters)) {
            sendReportStatusEmails(favoriters, document, remediatedByUsername,
                    String.format("Document remediated: %s", document.getFilename()),
                    "document-report-remediated-notification");
            log.info("Sent report remediation notifications to {} favoriters for document: {}",
                    favoriters.size(), document.getId());
        } else {
            log.info("No favoriter found for remediate report notification for document: {}", document.getId());
        }
    }

    @Override
    public void sendCommentReportProcessNotification(NotificationEventRequest notificationEvent) {
        DocumentInformation document = findDocument(notificationEvent.getDocumentId());
        DocumentComment comment = findCommentById(document.getId(), notificationEvent.getCommentId());
        User triggerUser = findUserById(notificationEvent.getTriggerUserId());

        if (comment.getFlag() == -1) {
            // Comment was resolved (removed)
            sendCommentResolvedNotifications(document, comment, triggerUser, notificationEvent);
        } else if (comment.getFlag() == 1) {
            // Comment report was rejected
            sendCommentRejectedNotifications(document, comment, triggerUser, notificationEvent);
        }
    }

    private void sendCommentResolvedNotifications(DocumentInformation document,
                                                  DocumentComment comment,
                                                  User triggerUser,
                                                  NotificationEventRequest event) {
        // Get comment reporters
        Set<UUID> reporterUserIds = commentReportRepository.findReporterUserIdsByCommentIdAndStatusAndTimes(
                event.getCommentId(), CommentReportStatus.RESOLVED, event.getVersionNumber());
        Set<User> reporters = new HashSet<>(userRepository.findUsersByUserIdIn(reporterUserIds));

        // Send notification to reporters
        if (CollectionUtils.isNotEmpty(reporters)) {
            sendCommentResolveReporterNotifications(reporters, triggerUser, document, event);
            log.info("Sent comment report notifications to {} reporters for comment: {}",
                    reporters.size(), event.getCommentId());
        } else {
            log.info("No reporters found for comment report notification for comment: {}",
                    event.getCommentId());
        }

        // Send notification to commenter
        User commenter = findUserById(comment.getUserId().toString());
        if (Objects.nonNull(commenter)) {
            sendCommentResolveCommenterNotifications(commenter, triggerUser, document, event);
            log.info("Sent comment report notification to commenter: {}", commenter.getUsername());
        } else {
            log.info("No commenter found for comment report notification for comment: {}",
                    event.getCommentId());
        }
    }

    private void sendCommentRejectedNotifications(DocumentInformation document,
                                                  DocumentComment comment,
                                                  User triggerUser,
                                                  NotificationEventRequest event) {
        Set<UUID> reporterUUIDs = commentReportRepository.findReporterUserIdsByCommentIdAndStatusAndTimes(
                event.getCommentId(), CommentReportStatus.REJECTED, event.getVersionNumber());

        // Send reject mail to reporters
        if (CollectionUtils.isNotEmpty(reporterUUIDs)) {
            Set<User> rejectedReporters = new HashSet<>(userRepository.findUsersByUserIdIn(reporterUUIDs));
            sendCommentRejectionReporterNotifications(rejectedReporters, triggerUser, document, event, comment);
            log.info("Sent comment report rejection notifications to {} reporters for comment: {}",
                    rejectedReporters.size(), event.getCommentId());
        } else {
            log.info("No reporters with rejected status found for comment: {}", event.getCommentId());
        }
    }

    private void sendCommentResolveCommenterNotifications(User commenter, User triggerUser,
                                                          DocumentInformation document,
                                                          NotificationEventRequest event) {
        if (commenter == null || commenter.getEmail() == null || commenter.getEmail().isEmpty()) {
            log.warn("Cannot send commenter notification: invalid commenter or email");
            return;
        }

        String subject = "Your comment has been moderated in document: " + document.getFilename();

        NotificationContext context = new NotificationContext()
                .withDocument(document)
                .withTriggerUser(triggerUser.getUsername())
                .withCommentId(event.getCommentId())
                .withRecipientName(commenter.getUsername());

        try {
            String htmlContent = renderTemplate("comment-report-resolved-creator-notification", context.getVariables());
            sendEmail(commenter.getEmail(), subject, htmlContent);
            log.info("Successfully sent moderation notification to commenter: {}", commenter.getUsername());
        } catch (MessagingException e) {
            log.error("Failed to send email to commenter: {}", commenter.getEmail(), e);
        }
    }

    private void sendCommentRejectionReporterNotifications(Set<User> reporters, User triggerUser,
                                                           DocumentInformation document,
                                                           NotificationEventRequest event,
                                                           DocumentComment comment) {
        Map<String, User> emailToUserMap = createEmailToUserMap(reporters);

        if (!emailToUserMap.isEmpty()) {
            String subject = "Comment Report Decision: Report Rejected";

            NotificationContext context = new NotificationContext()
                    .withDocument(document)
                    .withResolverUser(triggerUser.getUsername())
                    .withCommentId(event.getCommentId())
                    .withCommentContent(comment.getContent())
                    .withRecipientMap(emailToUserMap);

            sendBatchNotificationEmails(
                    emailToUserMap.keySet(),
                    subject,
                    "comment-report-rejected-reporter-notification",
                    context.getVariables()
            );
        }
    }

    private void sendCommentResolveReporterNotifications(Set<User> reporters, User triggerUser,
                                                         DocumentInformation document,
                                                         NotificationEventRequest event) {
        Map<String, User> emailToUserMap = createEmailToUserMap(reporters);

        if (!emailToUserMap.isEmpty()) {
            String subject = "Comment report processed for document: " + document.getFilename();

            NotificationContext context = new NotificationContext()
                    .withDocument(document)
                    .withResolverUser(triggerUser.getUsername())
                    .withCommentId(event.getCommentId())
                    .withRecipientMap(emailToUserMap);

            sendBatchNotificationEmails(
                    emailToUserMap.keySet(),
                    subject,
                    "comment-report-resolved-reporter-notification",
                    context.getVariables()
            );
        }
    }

    private DocumentInformation findDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }

    private DocumentComment findCommentById(String documentId, Long commentId) {
        return documentCommentRepository.findByDocumentIdAndId(documentId, commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    protected String getUsernameById(String userId) {
        Optional<User> user = userRepository.findById(UUID.fromString(userId));
        return user.map(User::getUsername).orElse("Unknown");
    }

    private User findUserById(String userId) {
        return userRepository.findById(UUID.fromString(userId)).orElse(null);
    }

    protected Set<User> findUsersToNotify(DocumentInformation document, User triggerUser) {
        // Get user IDs from document favorites
        Set<UUID> favoriteUserIds = documentFavoriteRepository.findUserIdsByDocumentId(document.getId());

        // Remove the trigger user from notifications if they favorited the document
        if (Objects.nonNull(triggerUser)) {
            favoriteUserIds.removeIf(userId -> userId.equals(triggerUser.getUserId()));
        }

        // Fetch all users that need to be notified
        return new HashSet<>(userRepository.findUsersByUserIdIn(favoriteUserIds));
    }

    private Set<User> findFavoriters(DocumentInformation document) {
        Set<UUID> favoriterUserIds = documentFavoriteRepository.findUserIdsByDocumentId(document.getId());

        if (CollectionUtils.isEmpty(favoriterUserIds)) {
            return new HashSet<>();
        }

        return new HashSet<>(userRepository.findUsersByUserIdIn(favoriterUserIds));
    }

    protected Map<String, User> createEmailToUserMap(Set<User> users) {
        return users.stream()
                .filter(user -> user.getEmail() != null && !user.getEmail().isEmpty())
                .collect(Collectors.toMap(User::getEmail, user -> user));
    }

    private void sendNotifications(Set<User> users, User triggerUser, DocumentInformation document,
                                   NotificationEventRequest event) {
        Map<String, User> emailToUserMap = createEmailToUserMap(users);

        if (!emailToUserMap.isEmpty()) {
            String subject = buildEmailSubject(event, document);
            String template = determineEmailTemplate(event.getNotificationType());

            NotificationContext context = buildNotificationContext(event, document, triggerUser, emailToUserMap);

            sendBatchNotificationEmails(
                    emailToUserMap.keySet(),
                    subject,
                    template,
                    context.getVariables()
            );
        }
    }

    protected NotificationContext buildNotificationContext(NotificationEventRequest event,
                                                         DocumentInformation document,
                                                         User triggerUser,
                                                         Map<String, User> emailToUserMap) {
        NotificationContext context = new NotificationContext()
                .withDocument(document)
                .withTriggerUser(triggerUser.getUsername())
                .withRecipientMap(emailToUserMap);

        // Add specific variables based on notification type
        switch (event.getNotificationType()) {
            case NEW_COMMENT_FROM_NEW_USER:
                context.withActionType("commented on");
                break;
            case NEW_FILE_VERSION:
                context.withActionType("uploaded a new version of");
                context.withVersionNumber(event.getVersionNumber());
                break;
            case DOCUMENT_REVERTED:
                context.withActionType("reverted");
                context.withRevertedVersion(event.getVersionNumber());
                break;
        }

        return context;
    }

    private String buildEmailSubject(NotificationEventRequest event, DocumentInformation document) {
        return switch (event.getNotificationType()) {
            case NEW_COMMENT_FROM_NEW_USER -> String.format("New comment on document: %s", document.getFilename());
            case NEW_FILE_VERSION -> String.format("New version uploaded for document: %s", document.getFilename());
            case DOCUMENT_REVERTED -> String.format("Document reverted: %s", document.getFilename());
        };
    }

    private String determineEmailTemplate(NotificationType type) {
        return switch (type) {
            case NEW_COMMENT_FROM_NEW_USER -> "new-comment-notification";
            case NEW_FILE_VERSION -> "new-version-notification";
            case DOCUMENT_REVERTED -> "document-reverted-notification";
        };
    }

    private void sendReportStatusEmails(Set<User> recipients, DocumentInformation document,
                                        String actionUsername, String subject, String emailTemplate) {
        Map<String, User> emailToUserMap = createEmailToUserMap(recipients);

        if (!emailToUserMap.isEmpty()) {
            NotificationContext context = new NotificationContext()
                    .withDocument(document)
                    .withActionUser(actionUsername)
                    .withRecipientMap(emailToUserMap);

            sendBatchNotificationEmails(
                    emailToUserMap.keySet(),
                    subject,
                    emailTemplate,
                    context.getVariables()
            );
        }
    }

    private void sendMailToCreator(String subject, String templateName, User creator,
                                   DocumentInformation document, String actionUsername) {
        try {
            NotificationContext context = new NotificationContext()
                    .withDocument(document)
                    .withActionUser(actionUsername)
                    .withRecipientName(creator.getUsername());

            String htmlContent = renderTemplate(templateName, context.getVariables());
            sendEmail(creator.getEmail(), subject, htmlContent);
        } catch (MessagingException e) {
            log.error("Failed to send email to creator: {}", creator.getEmail(), e);
        }
    }

}