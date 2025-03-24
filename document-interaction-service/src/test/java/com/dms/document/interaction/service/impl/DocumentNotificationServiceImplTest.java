package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.dto.NotificationEventRequest;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.NotificationType;
import com.dms.document.interaction.model.DocumentFavorite;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.repository.DocumentCommentRepository;
import com.dms.document.interaction.repository.DocumentFavoriteRepository;
import com.dms.document.interaction.service.PublishEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentNotificationServiceImplTest {

    @Mock
    private DocumentFavoriteRepository documentFavoriteRepository;

    @Mock
    private DocumentCommentRepository documentCommentRepository;

    @Mock
    private PublishEventService publishEventService;

    @InjectMocks
    private DocumentNotificationServiceImpl documentNotificationService;

    @Captor
    private ArgumentCaptor<NotificationEventRequest> eventRequestCaptor;

    private DocumentInformation sampleDocument;
    private UUID documentOwnerId;
    private UUID commenterId;
    private UUID favoriterUserId1;
    private UUID favoriterUserId2;
    private Long commentId;
    private String documentId;

    @BeforeEach
    void setUp() {
        documentOwnerId = UUID.randomUUID();
        commenterId = UUID.randomUUID();
        favoriterUserId1 = UUID.randomUUID();
        favoriterUserId2 = UUID.randomUUID();
        commentId = 123456L;
        documentId = "doc-123";

        sampleDocument = new DocumentInformation();
        sampleDocument.setId(documentId);
        sampleDocument.setUserId(documentOwnerId.toString());
        sampleDocument.setFilename("test-document.pdf");
        sampleDocument.setCurrentVersion(1);
    }

    @Test
    void handleCommentNotification_WhenNewCommenter_ShouldSendNotification() {
        // Arrange
        String commenterUsername = "test-commenter";

        // Mock document favorites
        DocumentFavorite favorite1 = new DocumentFavorite();
        favorite1.setUserId(favoriterUserId1);

        DocumentFavorite favorite2 = new DocumentFavorite();
        favorite2.setUserId(favoriterUserId2);

        // Set up the repository mocks
        when(documentCommentRepository.existsByDocumentIdAndUserIdAndIdNot(documentId, commenterId, commentId))
                .thenReturn(false); // User has not commented before
        when(documentFavoriteRepository.findByDocumentId(documentId))
                .thenReturn(List.of(favorite1, favorite2));

        // Act
        documentNotificationService.handleCommentNotification(sampleDocument, commenterUsername, commenterId, commentId);

        // Assert
        verify(publishEventService, times(1)).sendNotificationEvent(eventRequestCaptor.capture());

        NotificationEventRequest capturedRequest = eventRequestCaptor.getValue();
        assertEquals(EventType.FAVORITE_NOTIFICATION.name(), capturedRequest.getSubject());
        assertEquals(documentId, capturedRequest.getDocumentId());
        assertEquals(commenterUsername, capturedRequest.getTriggerUserId());
        assertEquals(NotificationType.NEW_COMMENT_FROM_NEW_USER, capturedRequest.getNotificationType());
    }

    @Test
    void handleCommentNotification_WhenExistingCommenter_ShouldNotSendNotification() {
        // Arrange
        String commenterUsername = "test-commenter";

        // Set up the repository mock to indicate this user has commented before
        when(documentCommentRepository.existsByDocumentIdAndUserIdAndIdNot(documentId, commenterId, commentId))
                .thenReturn(true); // User has commented before

        // Act
        documentNotificationService.handleCommentNotification(sampleDocument, commenterUsername, commenterId, commentId);

        // Assert
        verify(documentFavoriteRepository, never()).findByDocumentId(anyString());
        verify(publishEventService, never()).sendNotificationEvent(any());
    }

    @Test
    void handleCommentNotification_WhenNoFavorites_ShouldNotSendNotification() {
        // Arrange
        String commenterUsername = "test-commenter";

        // Set up the repository mocks
        when(documentCommentRepository.existsByDocumentIdAndUserIdAndIdNot(documentId, commenterId, commentId))
                .thenReturn(false); // User has not commented before
        when(documentFavoriteRepository.findByDocumentId(documentId))
                .thenReturn(List.of()); // No favorites

        // Act
        documentNotificationService.handleCommentNotification(sampleDocument, commenterUsername, commenterId, commentId);

        // Assert
        verify(publishEventService, never()).sendNotificationEvent(any());
    }

    @Test
    void handleFileVersionNotification_ShouldNotifyFavoriters() {
        // Arrange
        String updaterUsername = "test-updater";
        Integer versionNumber = 2;

        // Mock document favorites, excluding document owner
        DocumentFavorite favorite1 = new DocumentFavorite();
        favorite1.setUserId(favoriterUserId1);

        DocumentFavorite favorite2 = new DocumentFavorite();
        favorite2.setUserId(favoriterUserId2);

        DocumentFavorite ownerFavorite = new DocumentFavorite();
        ownerFavorite.setUserId(UUID.fromString(sampleDocument.getUserId()));

        when(documentFavoriteRepository.findByDocumentId(documentId))
                .thenReturn(List.of(favorite1, favorite2, ownerFavorite));

        // Act
        documentNotificationService.handleFileVersionNotification(sampleDocument, updaterUsername, versionNumber);

        // Assert
        verify(publishEventService, times(1)).sendNotificationEvent(eventRequestCaptor.capture());

        NotificationEventRequest capturedRequest = eventRequestCaptor.getValue();
        assertEquals(EventType.FAVORITE_NOTIFICATION.name(), capturedRequest.getSubject());
        assertEquals(documentId, capturedRequest.getDocumentId());
        assertEquals(updaterUsername, capturedRequest.getTriggerUserId());
        assertEquals(NotificationType.NEW_FILE_VERSION, capturedRequest.getNotificationType());
        assertEquals(versionNumber, capturedRequest.getVersionNumber());
    }

    @Test
    void handleFileVersionNotification_WhenNoFavorites_ShouldNotSendNotification() {
        // Arrange
        String updaterUsername = "test-updater";
        Integer versionNumber = 2;

        when(documentFavoriteRepository.findByDocumentId(documentId))
                .thenReturn(List.of());

        // Act
        documentNotificationService.handleFileVersionNotification(sampleDocument, updaterUsername, versionNumber);

        // Assert
        verify(publishEventService, never()).sendNotificationEvent(any());
    }

    @Test
    void handleRevertNotification_ShouldNotifyFavoriters() {
        // Arrange
        String userWhoReverted = "reverter-user";
        Integer versionNumber = 1;

        // Mock document favorites, excluding document owner
        DocumentFavorite favorite1 = new DocumentFavorite();
        favorite1.setUserId(favoriterUserId1);

        DocumentFavorite favorite2 = new DocumentFavorite();
        favorite2.setUserId(favoriterUserId2);

        when(documentFavoriteRepository.findByDocumentId(documentId))
                .thenReturn(List.of(favorite1, favorite2));

        // Act
        documentNotificationService.handleRevertNotification(sampleDocument, userWhoReverted, versionNumber);

        // Assert
        verify(publishEventService, times(1)).sendNotificationEvent(eventRequestCaptor.capture());

        NotificationEventRequest capturedRequest = eventRequestCaptor.getValue();
        assertEquals(EventType.FAVORITE_NOTIFICATION.name(), capturedRequest.getSubject());
        assertEquals(documentId, capturedRequest.getDocumentId());
        assertEquals(userWhoReverted, capturedRequest.getTriggerUserId());
        assertEquals(NotificationType.DOCUMENT_REVERTED, capturedRequest.getNotificationType());
        assertEquals(versionNumber, capturedRequest.getVersionNumber());
    }

    @Test
    void handleRevertNotification_WhenNoFavorites_ShouldNotSendNotification() {
        // Arrange
        String userWhoReverted = "reverter-user";
        Integer versionNumber = 1;

        when(documentFavoriteRepository.findByDocumentId(documentId))
                .thenReturn(List.of());

        // Act
        documentNotificationService.handleRevertNotification(sampleDocument, userWhoReverted, versionNumber);

        // Assert
        verify(publishEventService, never()).sendNotificationEvent(any());
    }

    @Test
    void sendCommentReportResolvedNotification_ShouldSendNotification() {
        // Arrange
        String documentId = "doc-123";
        Long commentId = 456L;
        UUID adminId = UUID.randomUUID();
        int times = 3;

        // Act
        documentNotificationService.sendCommentReportResolvedNotification(documentId, commentId, adminId, times);

        // Assert
        verify(publishEventService, times(1)).sendNotificationEvent(eventRequestCaptor.capture());

        NotificationEventRequest capturedRequest = eventRequestCaptor.getValue();
        assertEquals(EventType.COMMENT_REPORT_PROCESS_EVENT.name(), capturedRequest.getSubject());
        assertEquals(documentId, capturedRequest.getDocumentId());
        assertEquals(commentId, capturedRequest.getCommentId());
        assertEquals(adminId.toString(), capturedRequest.getTriggerUserId());
        assertEquals(times, capturedRequest.getVersionNumber());
    }
}