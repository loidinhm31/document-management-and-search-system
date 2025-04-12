package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.dto.NotificationEventRequest;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.NotificationType;
import com.dms.document.interaction.producer.RabbitMQMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublishEventServiceImplTest {

    private PublishEventServiceImpl publishEventService;

    @Mock
    private RabbitMQMessageProducer rabbitMQMessageProducer;

    private final String documentExchange = "test-document-exchange";
    private final String notificationExchange = "test-notification-exchange";
    private final String documentProcessRoutingKey = "test-document-process-routing-key";

    @BeforeEach
    void setUp() {
        publishEventService = new PublishEventServiceImpl(rabbitMQMessageProducer);

        // Set private fields using ReflectionTestUtils
        ReflectionTestUtils.setField(publishEventService, "documentExchange", documentExchange);
        ReflectionTestUtils.setField(publishEventService, "notificationExchange", notificationExchange);
        ReflectionTestUtils.setField(publishEventService, "documentProcessRoutingKey", documentProcessRoutingKey);
    }

    @Test
    void sendSyncEvent_shouldPublishToCorrectExchangeAndRoutingKey() {
        // Arrange
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String documentId = "doc-123";

        SyncEventRequest syncEventRequest = SyncEventRequest.builder()
                .eventId(eventId)
                .userId(userId)
                .documentId(documentId)
                .subject(EventType.SYNC_EVENT.name())
                .triggerAt(Instant.now())
                .build();

        // Act
        publishEventService.sendSyncEvent(syncEventRequest);

        // Assert
        verify(rabbitMQMessageProducer, times(1))
                .publish(syncEventRequest, documentExchange, documentProcessRoutingKey);
    }

    @Test
    void sendSyncEvent_shouldPassCorrectEventDetails() {
        // Arrange
        String eventId = UUID.randomUUID().toString();
        String userId = "user-123";
        String documentId = "doc-123";
        String subject = EventType.UPDATE_EVENT.name();
        Instant triggerAt = Instant.now();
        Integer versionNumber = 2;

        SyncEventRequest syncEventRequest = SyncEventRequest.builder()
                .eventId(eventId)
                .userId(userId)
                .documentId(documentId)
                .subject(subject)
                .triggerAt(triggerAt)
                .versionNumber(versionNumber)
                .build();

        ArgumentCaptor<SyncEventRequest> requestCaptor = ArgumentCaptor.forClass(SyncEventRequest.class);
        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        publishEventService.sendSyncEvent(syncEventRequest);

        // Assert
        verify(rabbitMQMessageProducer).publish(requestCaptor.capture(), exchangeCaptor.capture(), routingKeyCaptor.capture());

        SyncEventRequest capturedRequest = requestCaptor.getValue();
        assertEquals(eventId, capturedRequest.getEventId());
        assertEquals(userId, capturedRequest.getUserId());
        assertEquals(documentId, capturedRequest.getDocumentId());
        assertEquals(subject, capturedRequest.getSubject());
        assertEquals(triggerAt, capturedRequest.getTriggerAt());
        assertEquals(versionNumber, capturedRequest.getVersionNumber());

        assertEquals(documentExchange, exchangeCaptor.getValue());
        assertEquals(documentProcessRoutingKey, routingKeyCaptor.getValue());
    }

    @Test
    void sendNotificationEvent_shouldPublishToCorrectExchangeAndRoutingKey() {
        // Arrange
        NotificationEventRequest notificationEvent = NotificationEventRequest.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(UUID.randomUUID().toString())
                .documentId("doc-123")
                .subject(EventType.FAVORITE_NOTIFICATION.name())
                .triggerAt(Instant.now())
                .build();

        // Act
        publishEventService.sendNotificationEvent(notificationEvent);

        // Assert
        verify(rabbitMQMessageProducer, times(1))
                .publish(notificationEvent, notificationExchange, documentProcessRoutingKey);
    }

    @Test
    void sendNotificationEvent_shouldPassCorrectEventDetails() {
        // Arrange
        String eventId = UUID.randomUUID().toString();
        String userId = "user-123";
        String documentId = "doc-123";
        Long commentId = 12345L;
        String documentTitle = "Test Document";
        NotificationType notificationType = NotificationType.NEW_COMMENT_FROM_NEW_USER;
        String triggerUserId = "trigger-user-123";
        Integer versionNumber = 3;
        Instant triggerAt = Instant.now();
        String subject = EventType.FAVORITE_NOTIFICATION.name();

        NotificationEventRequest notificationEvent = NotificationEventRequest.builder()
                .eventId(eventId)
                .userId(userId)
                .documentId(documentId)
                .commentId(commentId)
                .documentTitle(documentTitle)
                .notificationType(notificationType)
                .triggerUserId(triggerUserId)
                .versionNumber(versionNumber)
                .triggerAt(triggerAt)
                .subject(subject)
                .build();

        ArgumentCaptor<NotificationEventRequest> requestCaptor = ArgumentCaptor.forClass(NotificationEventRequest.class);
        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        publishEventService.sendNotificationEvent(notificationEvent);

        // Assert
        verify(rabbitMQMessageProducer).publish(requestCaptor.capture(), exchangeCaptor.capture(), routingKeyCaptor.capture());

        NotificationEventRequest capturedRequest = requestCaptor.getValue();
        assertEquals(eventId, capturedRequest.getEventId());
        assertEquals(userId, capturedRequest.getUserId());
        assertEquals(documentId, capturedRequest.getDocumentId());
        assertEquals(commentId, capturedRequest.getCommentId());
        assertEquals(documentTitle, capturedRequest.getDocumentTitle());
        assertEquals(notificationType, capturedRequest.getNotificationType());
        assertEquals(triggerUserId, capturedRequest.getTriggerUserId());
        assertEquals(versionNumber, capturedRequest.getVersionNumber());
        assertEquals(triggerAt, capturedRequest.getTriggerAt());
        assertEquals(subject, capturedRequest.getSubject());

        assertEquals(notificationExchange, exchangeCaptor.getValue());
        assertEquals(documentProcessRoutingKey, routingKeyCaptor.getValue());
    }

    @Test
    void sendNotificationEvent_withCommentReportEvent_shouldPublishCorrectly() {
        // Arrange
        String eventId = UUID.randomUUID().toString();
        String documentId = "doc-123";
        Long commentId = 12345L;
        String triggerUserId = UUID.randomUUID().toString();
        int times = 3;

        NotificationEventRequest notificationEvent = NotificationEventRequest.builder()
                .eventId(eventId)
                .documentId(documentId)
                .commentId(commentId)
                .triggerUserId(triggerUserId)
                .triggerAt(Instant.now())
                .subject(EventType.COMMENT_REPORT_PROCESS_EVENT.name())
                .versionNumber(times)
                .build();

        // Act
        publishEventService.sendNotificationEvent(notificationEvent);

        // Assert
        verify(rabbitMQMessageProducer, times(1))
                .publish(notificationEvent, notificationExchange, documentProcessRoutingKey);
    }

    @Test
    void sendSyncEvent_withNullRequest_shouldNotThrowException() {
        // Act & Assert (no exception should be thrown)
        publishEventService.sendSyncEvent(null);

        // Verify the call was made with null
        verify(rabbitMQMessageProducer, times(1))
                .publish(null, documentExchange, documentProcessRoutingKey);
    }

    @Test
    void sendNotificationEvent_withNullRequest_shouldNotThrowException() {
        // Act & Assert (no exception should be thrown)
        publishEventService.sendNotificationEvent(null);

        // Verify the call was made with null
        verify(rabbitMQMessageProducer, times(1))
                .publish(null, notificationExchange, documentProcessRoutingKey);
    }
}