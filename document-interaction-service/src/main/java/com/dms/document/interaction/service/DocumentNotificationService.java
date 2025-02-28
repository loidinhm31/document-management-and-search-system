package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.NotificationEventRequest;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.NotificationType;
import com.dms.document.interaction.model.DocumentFavorite;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.repository.DocumentCommentRepository;
import com.dms.document.interaction.repository.DocumentFavoriteRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentNotificationService {
    private final DocumentFavoriteRepository documentFavoriteRepository;
    private final DocumentCommentRepository documentCommentRepository;
    private final DocumentRepository documentRepository;
    private final PublishEventService publishEventService;

    public void handleCommentNotification(DocumentInformation document, String username, UUID userId, Long newCommentId) {
        // Only send notification if this is a new commenter
        if (isNewCommenter(document.getId(), userId, newCommentId)) {
            // Get users who favorited, excluding the commenter
            Set<UUID> favoritedUsers = documentFavoriteRepository.findByDocumentId(document.getId()).stream()
                    .map(DocumentFavorite::getUserId)
                    .filter(favUserId -> !favUserId.equals(userId))
                    .collect(Collectors.toSet());

            if (CollectionUtils.isNotEmpty(favoritedUsers)) {
                sendNotification(
                        document,
                        username,
                        NotificationType.NEW_COMMENT_FROM_NEW_USER,
                        null
                );
            }
        }
    }

    public void handleFileVersionNotification(DocumentInformation document, String username, Integer versionNumber) {
        Set<UUID> favoritedUsers = documentFavoriteRepository.findByDocumentId(document.getId()).stream()
                .map(DocumentFavorite::getUserId)
                .filter(favUserId -> !favUserId.equals(UUID.fromString(document.getUserId())))
                .collect(Collectors.toSet());

        if (!favoritedUsers.isEmpty()) {
            sendNotification(
                    document,
                    username,
                    NotificationType.NEW_FILE_VERSION,
                    versionNumber
            );
        }
    }

    public void handleRevertNotification(DocumentInformation document, String username, Integer versionNumber) {
        Set<UUID> favoritedUsers = documentFavoriteRepository.findByDocumentId(document.getId()).stream()
                .map(DocumentFavorite::getUserId)
                .filter(favUserId -> !favUserId.equals(UUID.fromString(document.getUserId())))
                .collect(Collectors.toSet());

        if (!favoritedUsers.isEmpty()) {
            sendNotification(
                    document,
                    username,
                    NotificationType.DOCUMENT_REVERTED,
                    versionNumber
            );
        }
    }

    public void sendCommentReportResolvedNotification(String documentId, Long commentId, boolean resolved, UUID adminId) {
        if (resolved) {
            NotificationEventRequest notificationEvent = NotificationEventRequest.builder()
                    .eventId(UUID.randomUUID().toString())
                    .documentId(documentId)
                    .commentId(commentId)
                    .triggerUserId(adminId.toString())
                    .triggerAt(Instant.now())
                    .subject(EventType.COMMENT_REPORT_PROCESS_EVENT.name())
                    .build();

            publishEventService.sendNotificationEvent(notificationEvent);
        }
    }

    private boolean isNewCommenter(String documentId, UUID userId, Long newCommentId) {
        // Check if this user has commented before on this document
        return !documentCommentRepository.existsByDocumentIdAndUserIdAndIdNot(documentId, userId, newCommentId);
    }

    private void sendNotification(
            DocumentInformation document,
            String triggerUsername,
            NotificationType type,
            Integer versionNumber) {

        NotificationEventRequest notificationEvent = NotificationEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .documentId(document.getId())
                .documentTitle(document.getFilename())
                .notificationType(type)
                .triggerUserId(triggerUsername)
                .versionNumber(versionNumber)
                .triggerAt(Instant.now())
                .subject(EventType.FAVORITE_NOTIFICATION.name())
                .build();

        publishEventService.sendNotificationEvent(notificationEvent);
    }
}