package com.dms.processor.consumer;

import com.dms.processor.dto.NotificationEventRequest;
import com.dms.processor.enums.NotificationType;
import com.dms.processor.model.DocumentInformation;
import com.dms.processor.model.User;
import com.dms.processor.repository.DocumentFavoriteRepository;
import com.dms.processor.repository.DocumentRepository;
import com.dms.processor.repository.UserRepository;
import com.dms.processor.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumer {
    @Value("${app.base-url}")
    private String baseUrl;

    private final EmailService emailService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentFavoriteRepository documentFavoriteRepository;

    @RabbitListener(queues = "${rabbitmq.queues.notification}")
    public void consumeNotificationEvent(NotificationEventRequest notificationEvent) {
        log.info("Consumed notification event: [Type: {}, Document: {}]",
                notificationEvent.getNotificationType(),
                notificationEvent.getDocumentId());

        try {
            DocumentInformation document = findDocument(notificationEvent.getDocumentId());
            Set<User> usersToNotify = findUsersToNotify(document, notificationEvent.getTriggerUsername());

            if (CollectionUtils.isNotEmpty(usersToNotify)) {
                sendNotifications(usersToNotify, document, notificationEvent);
                log.info("Sent notification emails to {} users for document: {}",
                        usersToNotify.size(), document.getId());
            } else {
                log.info("No recipients found for notification event {}", notificationEvent.getEventId());
            }
        } catch (Exception e) {
            log.error("Error processing notification event", e);
            throw e;
        }
    }

    private DocumentInformation findDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }

    private Set<User> findUsersToNotify(DocumentInformation document, String triggerUsername) {
        // Get user IDs from document_favorites table
        Set<UUID> favoriteUserIds = documentFavoriteRepository.findUserIdsByDocumentId(document.getId());

        // Remove the trigger user from notifications if they favorited the document
        userRepository.findByUsername(triggerUsername)
                .ifPresent(user -> favoriteUserIds.remove(user.getUserId()));

        // Fetch all users that need to be notified
        return new HashSet<>(userRepository.findUsersByUserIdIn(favoriteUserIds));
    }

    private void sendNotifications(Set<User> users, DocumentInformation document,
                                   NotificationEventRequest event) {
        Map<String, User> emailToUserMap = users.stream()
                .filter(user -> user.getEmail() != null && !user.getEmail().isEmpty())
                .collect(Collectors.toMap(User::getEmail, user -> user));

        if (!emailToUserMap.isEmpty()) {
            String subject = buildEmailSubject(event, document);
            String template = determineEmailTemplate(event.getNotificationType());

            emailService.sendBatchNotificationEmails(
                    emailToUserMap.keySet(),
                    subject,
                    template,
                    buildTemplateVariables(event, document, emailToUserMap)
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
                                                       Map<String, User> emailToUserMap) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("baseUrl", baseUrl);
        vars.put("documentTitle", document.getFilename());
        vars.put("triggerUser", event.getTriggerUsername());
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
}
