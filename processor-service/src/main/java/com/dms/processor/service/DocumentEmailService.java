package com.dms.processor.service;

import com.dms.processor.dto.NotificationEventRequest;
import com.dms.processor.enums.NotificationType;
import com.dms.processor.model.DocumentComment;
import com.dms.processor.model.DocumentInformation;
import com.dms.processor.model.User;
import com.dms.processor.repository.*;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentEmailService extends EmailService {
    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.email.batch-size:50}")
    private int batchSize;

    @Autowired
    private TemplateEngine templateEngine;

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

    public void sendNotifyForRelatedUserInDocument(NotificationEventRequest notificationEvent) {
        DocumentInformation document = findDocument(notificationEvent.getDocumentId());
        User triggerUser = userRepository.findByUsername(notificationEvent.getTriggerUserId())
                .orElseThrow(() -> new RuntimeException("Trigger user not found"));
        Set<User> usersToNotify = findUsersToNotify(document, triggerUser);

        if (CollectionUtils.isNotEmpty(usersToNotify)) {
            sendNotifications(usersToNotify, triggerUser, document, notificationEvent);
            log.info("Sent notification emails to {} users for document: {}",
                    usersToNotify.size(), document.getId());
        } else {
            log.info("No recipients found for notification event {}", notificationEvent.getEventId());
        }
    }

    public void sendReportStatusNotifications(DocumentInformation document, String resolverId) {
        Optional<User> resolver = userRepository.findById(UUID.fromString(resolverId));
        String resolvedByUsername = resolver.map(User::getUsername).orElse("Unknown");

        Set<User> favoriters = findFavoriters(document);
        Set<User> reporters = findReporters(document);

        // Combine both sets while avoiding duplicates
        Set<User> allRecipients = new HashSet<>();
        allRecipients.addAll(favoriters);
        allRecipients.addAll(reporters);

        if (CollectionUtils.isNotEmpty(allRecipients)) {
            sendReportStatusEmails(allRecipients, document, resolvedByUsername, "resolved");
            log.info("Sent report resolved notification emails to {} users for document: {}",
                    allRecipients.size(), document.getId());
        } else {
            log.info("No recipients found for report status notification for document: {}", document.getId());
        }
    }

    public void sendRemediationNotifications(DocumentInformation document, String remediatorId) {
        Optional<User> remediator = userRepository.findById(UUID.fromString(remediatorId));
        String remediatedByUsername = remediator.map(User::getUsername).orElse("Unknown");

        Set<User> favoriters = findFavoriters(document);

        if (CollectionUtils.isNotEmpty(favoriters)) {
            sendReportStatusEmails(favoriters, document, remediatedByUsername, "remediated");
            log.info("Sent report remediation notification emails to {} users for document: {}",
                    favoriters.size(), document.getId());
        } else {
            log.info("No recipients found for remediation notification for document: {}", document.getId());
        }
    }

    public void sendCommentReportProcessNotification(NotificationEventRequest notificationEvent) {
        // Get the document information
        DocumentInformation document = findDocument(notificationEvent.getDocumentId());

        // Get comment reporters
        Set<UUID> reporterUserIds = commentReportRepository.findReporterUserIdsByCommentId(notificationEvent.getCommentId());
        Set<User> reporters = new HashSet<>(userRepository.findUsersByUserIdIn(reporterUserIds));

        // Get commenter - Note: Comment content may have been removed/moderated already
        DocumentComment documentComment = documentCommentRepository.findByDocumentIdAndId(document.getId(), notificationEvent.getCommentId())
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        // Even if comment content was removed, we still need the user who made it
        User commenter = userRepository.findById(documentComment.getUserId())
                .orElseThrow(() -> new RuntimeException("Commenter user not found"));

        // Trigger user
        User triggerUser = userRepository.findById(UUID.fromString(notificationEvent.getTriggerUserId()))
                .orElseThrow(() -> new RuntimeException("Trigger user not found"));

        // Send notification to reporters
        if (CollectionUtils.isNotEmpty(reporters)) {
            sendCommentReportEmails(reporters, triggerUser, document, notificationEvent);
            log.info("Sent comment report notification emails to {} reporters for comment: {}",
                    reporters.size(), notificationEvent.getCommentId());
        } else {
            log.info("No reporters found for comment report notification for comment: {}", notificationEvent.getCommentId());
        }

        // Send notification to the commenter
        sendCommenterNotificationEmail(commenter, triggerUser, document, notificationEvent, documentComment);
        log.info("Sent comment report notification email to commenter: {}", commenter.getUsername());
    }

    private void sendCommenterNotificationEmail(User commenter, User triggerUser,
                                                DocumentInformation document,
                                                NotificationEventRequest event,
                                                DocumentComment documentComment) {
        if (commenter == null || commenter.getEmail() == null || commenter.getEmail().isEmpty()) {
            log.warn("Cannot send commenter notification: invalid commenter or email");
            return;
        }

        String subject = "Your comment has been moderated in document: " + document.getFilename();

        Map<String, User> emailToUserMap = new HashMap<>();
        emailToUserMap.put(commenter.getEmail(), commenter);

        Map<String, Object> templateVars = buildCommenterNotificationTemplateVariables(
                event, document, emailToUserMap, triggerUser.getUsername());

        try {
            // Create a copy of template vars for this specific recipient
            Map<String, Object> personalizedVars = new HashMap<>(templateVars);
            personalizedVars.put("recipientName", commenter.getUsername());

            // Prepare email content
            Context context = new Context();
            context.setVariables(personalizedVars);
            String htmlContent = templateEngine.process("comment-report-moderation-notification", context);

            sendEmail(commenter.getEmail(), subject, htmlContent);
            log.info("Successfully sent moderation notification to commenter: {}", commenter.getUsername());
        } catch (MessagingException e) {
            log.error("Failed to send email to commenter: {}", commenter.getEmail(), e);
        }
    }

    private Map<String, Object> buildCommenterNotificationTemplateVariables(NotificationEventRequest event,
                                                                            DocumentInformation document,
                                                                            Map<String, User> emailToUserMap,
                                                                            String triggerUsername) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("baseUrl", baseUrl);
        vars.put("documentTitle", document.getFilename());
        vars.put("moderatorUser", triggerUsername);
        vars.put("documentId", document.getId());
        vars.put("commentId", event.getCommentId());
        vars.put("recipientMap", emailToUserMap);

        return vars;
    }

    private void sendBatchNotificationEmails(Collection<String> toEmails, String subject,
                                             String templateName, Map<String, Object> templateVars) {
        if (toEmails == null || toEmails.isEmpty()) {
            log.info("No email recipients provided");
            return;
        }

        Map<String, User> recipientMap = (Map<String, User>) templateVars.get("recipientMap");

        // Split recipients into batches
        List<List<String>> batches = toEmails.stream()
                .collect(Collectors.groupingBy(email ->
                        toEmails.stream().toList().indexOf(email) / batchSize))
                .values()
                .stream()
                .toList();

        // Process each batch asynchronously
        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() ->
                        sendBatch(batch, subject, templateName, templateVars, recipientMap)))
                .toList();

        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error sending batch emails", throwable);
                    } else {
                        log.info("Successfully sent {} emails in {} batches",
                                toEmails.size(), batches.size());
                    }
                });
    }

    private DocumentInformation findDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }

    private Set<User> findUsersToNotify(DocumentInformation document, User triggerrUser) {
        // Get user IDs from document favorites
        Set<UUID> favoriteUserIds = documentFavoriteRepository.findUserIdsByDocumentId(document.getId());

        // Remove the trigger user from notifications if they favorited the document
        if (Objects.nonNull(triggerrUser)) {
            favoriteUserIds.removeIf(userId -> userId.equals(triggerrUser.getUserId()));
        }

        // Fetch all users that need to be notified
        return new HashSet<>(userRepository.findUsersByUserIdIn(favoriteUserIds));
    }

    private void sendNotifications(Set<User> users, User triggerUser, DocumentInformation document,
                                   NotificationEventRequest event) {
        Map<String, User> emailToUserMap = users.stream()
                .filter(user -> user.getEmail() != null && !user.getEmail().isEmpty())
                .collect(Collectors.toMap(User::getEmail, user -> user));

        if (!emailToUserMap.isEmpty()) {
            String subject = buildEmailSubject(event, document);
            String template = determineEmailTemplate(event.getNotificationType());

            sendBatchNotificationEmails(
                    emailToUserMap.keySet(),
                    subject,
                    template,
                    buildTemplateVariables(event, document, emailToUserMap, triggerUser.getUsername())
            );
        }
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

    private Map<String, Object> buildTemplateVariables(NotificationEventRequest event,
                                                       DocumentInformation document,
                                                       Map<String, User> emailToUserMap,
                                                       String triggerUsername) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("baseUrl", baseUrl);
        vars.put("documentTitle", document.getFilename());
        vars.put("triggerUser", triggerUsername);
        vars.put("documentId", document.getId());
        vars.put("recipientMap", emailToUserMap);

        switch (event.getNotificationType()) {
            case NEW_COMMENT_FROM_NEW_USER:
                vars.put("actionType", "commented on");
                break;
            case NEW_FILE_VERSION:
                vars.put("actionType", "uploaded a new version of");
                vars.put("versionNumber", event.getVersionNumber());
                break;
            case DOCUMENT_REVERTED:
                vars.put("actionType", "reverted");
                vars.put("revertedToVersion", event.getVersionNumber());
                break;
        }

        return vars;
    }

    private void sendBatch(List<String> batchEmails, String subject, String templateName,
                           Map<String, Object> templateVars, Map<String, User> recipientMap) {
        try {
            for (String email : batchEmails) {
                try {
                    // Create a copy of template vars for this specific recipient
                    Map<String, Object> personalizedVars = new HashMap<>(templateVars);
                    User recipient = recipientMap.get(email);
                    personalizedVars.put("recipientName", recipient.getUsername());

                    // Prepare email content
                    Context context = new Context();
                    context.setVariables(personalizedVars);
                    String htmlContent = templateEngine.process(templateName, context);

                    sendEmail(email, subject, htmlContent);
                } catch (MessagingException e) {
                    log.error("Failed to send email to {}", email, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process email batch", e);
            throw new RuntimeException("Failed to send batch emails", e);
        }
    }

    private Set<User> findReporters(DocumentInformation document) {
        Set<UUID> reporterUserIds = documentReportRepository.findReporterUserIdsByDocumentId(document.getId());

        if (CollectionUtils.isEmpty(reporterUserIds)) {
            return new HashSet<>();
        }

        return new HashSet<>(userRepository.findUsersByUserIdIn(reporterUserIds));
    }

    private Set<User> findFavoriters(DocumentInformation document) {
        Set<UUID> favoriterUserIds = documentFavoriteRepository.findUserIdsByDocumentId(document.getId());

        if (CollectionUtils.isEmpty(favoriterUserIds)) {
            return new HashSet<>();
        }

        return new HashSet<>(userRepository.findUsersByUserIdIn(favoriterUserIds));
    }

    private void sendReportStatusEmails(Set<User> recipients, DocumentInformation document,
                                        String actionUsername, String actionType) {
        Map<String, User> emailToUserMap = recipients.stream()
                .filter(user -> user.getEmail() != null && !user.getEmail().isEmpty())
                .collect(Collectors.toMap(User::getEmail, user -> user));

        if (!emailToUserMap.isEmpty()) {
            String subject = buildReportStatusEmailSubject(document, actionType);
            String template = determineReportStatusTemplate(actionType);

            sendBatchNotificationEmails(
                    emailToUserMap.keySet(),
                    subject,
                    template,
                    buildReportStatusTemplateVariables(document, actionUsername, emailToUserMap, actionType)
            );
        }
    }

    private String buildReportStatusEmailSubject(DocumentInformation document, String actionType) {
        return switch (actionType) {
            case "resolved" -> String.format("Report resolved for document: %s", document.getFilename());
            case "remediated" -> String.format("Document remediated: %s", document.getFilename());
            default -> String.format("Document report status updated: %s", document.getFilename());
        };
    }

    private String determineReportStatusTemplate(String actionType) {
        return switch (actionType) {
            case "resolved" -> "document-report-resolved-notification";
            case "remediated" -> "document-report-remediated-notification";
            default -> "report-status-notification";
        };
    }

    private Map<String, Object> buildReportStatusTemplateVariables(DocumentInformation document,
                                                                   String actionUsername,
                                                                   Map<String, User> emailToUserMap,
                                                                   String actionType) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("baseUrl", baseUrl);
        vars.put("documentTitle", document.getFilename());
        vars.put("actionUser", actionUsername);
        vars.put("documentId", document.getId());
        vars.put("recipientMap", emailToUserMap);
        vars.put("actionType", actionType);

        return vars;
    }

    private void sendCommentReportEmails(Set<User> reporters, User triggerUser,
                                         DocumentInformation document,
                                         NotificationEventRequest event) {
        Map<String, User> emailToUserMap = reporters.stream()
                .filter(user -> user.getEmail() != null && !user.getEmail().isEmpty())
                .collect(Collectors.toMap(User::getEmail, user -> user));

        if (!emailToUserMap.isEmpty()) {
            String subject = "Comment report processed for document: " + document.getFilename();

            sendBatchNotificationEmails(
                    emailToUserMap.keySet(),
                    subject,
                    "comment-report-processed-notification",
                    buildCommentReportTemplateVariables(event, document, emailToUserMap, triggerUser.getUsername())
            );
        }
    }

    private Map<String, Object> buildCommentReportTemplateVariables(NotificationEventRequest event,
                                                                    DocumentInformation document,
                                                                    Map<String, User> emailToUserMap,
                                                                    String triggerUsername) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("baseUrl", baseUrl);
        vars.put("documentTitle", document.getFilename());
        vars.put("resolverUser", triggerUsername);
        vars.put("documentId", document.getId());
        vars.put("commentId", event.getCommentId());
        vars.put("recipientMap", emailToUserMap);

        return vars;
    }
}