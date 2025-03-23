package com.dms.auth.service.impl;

import com.dms.auth.dto.EmailNotificationPayload;
import com.dms.auth.entity.User;
import com.dms.auth.producer.RabbitMQMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PublishEventServiceImplTest {

    @Mock
    private RabbitMQMessageProducer rabbitMQMessageProducer;

    @InjectMocks
    private PublishEventServiceImpl publishEventService;

    @Captor
    private ArgumentCaptor<EmailNotificationPayload> payloadCaptor;

    @Captor
    private ArgumentCaptor<String> exchangeCaptor;

    @Captor
    private ArgumentCaptor<String> routingKeyCaptor;

    private User testUser;
    private final String notificationExchange = "notification.exchange";
    private final String emailAuthRoutingKey = "notification.email-auth.routing-key";
    private final int otpExpiryMinutes = 5;
    private final int maxAttempts = 5;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = new User();
        testUser.setUserId(java.util.UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        // Set up reflection fields
        ReflectionTestUtils.setField(publishEventService, "notificationExchange", notificationExchange);
        ReflectionTestUtils.setField(publishEventService, "emailAuthRoutingKey", emailAuthRoutingKey);
        ReflectionTestUtils.setField(publishEventService, "otpExpiryMinutes", otpExpiryMinutes);
        ReflectionTestUtils.setField(publishEventService, "maxAttempts", maxAttempts);
    }

    @Test
    void sendOtpEmailShouldPublishCorrectPayload() {
        // Given
        String otp = "123456";

        // When
        publishEventService.sendOtpEmail(testUser, otp);

        // Then
        verify(rabbitMQMessageProducer).publish(
                payloadCaptor.capture(),
                exchangeCaptor.capture(),
                routingKeyCaptor.capture());

        EmailNotificationPayload capturedPayload = payloadCaptor.getValue();
        assertEquals(testUser.getEmail(), capturedPayload.getTo());
        assertEquals(testUser.getUsername(), capturedPayload.getUsername());
        assertEquals(otp, capturedPayload.getOtp());
        assertEquals(otpExpiryMinutes, capturedPayload.getExpiryMinutes());
        assertEquals(maxAttempts, capturedPayload.getMaxAttempts());
        assertEquals("VERIFY_OTP", capturedPayload.getEventType());

        assertEquals(notificationExchange, exchangeCaptor.getValue());
        assertEquals(emailAuthRoutingKey, routingKeyCaptor.getValue());
    }

    @Test
    void sendOtpEmailShouldHandleRabbitMQException() {
        // Given
        String otp = "123456";
        doThrow(new RuntimeException("RabbitMQ connection error"))
                .when(rabbitMQMessageProducer).publish(any(), anyString(), anyString());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            publishEventService.sendOtpEmail(testUser, otp);
        });
        assertEquals("Failed to send OTP email", exception.getMessage());
    }

    @Test
    void sendPasswordResetEmailShouldPublishCorrectPayload() {
        // Given
        String token = "reset-token-123";
        int expiryMinutes = 60 * 5; // 5 hours

        // When
        publishEventService.sendPasswordResetEmail(testUser, token, expiryMinutes);

        // Then
        verify(rabbitMQMessageProducer).publish(
                payloadCaptor.capture(),
                exchangeCaptor.capture(),
                routingKeyCaptor.capture());

        EmailNotificationPayload capturedPayload = payloadCaptor.getValue();
        assertEquals(testUser.getEmail(), capturedPayload.getTo());
        assertEquals(testUser.getUsername(), capturedPayload.getUsername());
        assertEquals(token, capturedPayload.getToken());
        assertEquals(expiryMinutes, capturedPayload.getExpiryMinutes());
        assertEquals("PASSWORD_RESET", capturedPayload.getEventType());

        assertEquals(notificationExchange, exchangeCaptor.getValue());
        assertEquals(emailAuthRoutingKey, routingKeyCaptor.getValue());
    }

    @Test
    void sendPasswordResetEmailShouldHandleRabbitMQException() {
        // Given
        String token = "reset-token-123";
        int expiryMinutes = 60 * 5;
        doThrow(new RuntimeException("RabbitMQ connection error"))
                .when(rabbitMQMessageProducer).publish(any(), anyString(), anyString());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            publishEventService.sendPasswordResetEmail(testUser, token, expiryMinutes);
        });
        assertEquals("Failed to send password reset email", exception.getMessage());
    }

    @Test
    void sendOtpEmailShouldHandleNullUser() {
        // Given
        String otp = "123456";
        User nullUser = null;

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            publishEventService.sendOtpEmail(nullUser, otp);
        });
        assertEquals("Failed to send OTP email", exception.getMessage());
        // Verify that the root cause is a NullPointerException
        assertEquals(NullPointerException.class, exception.getCause().getClass());
    }

    @Test
    void sendPasswordResetEmailShouldHandleNullUser() {
        // Given
        String token = "reset-token-123";
        int expiryMinutes = 60 * 5;
        User nullUser = null;

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            publishEventService.sendPasswordResetEmail(nullUser, token, expiryMinutes);
        });
        assertEquals("Failed to send password reset email", exception.getMessage());
        // Verify that the root cause is a NullPointerException
        assertEquals(NullPointerException.class, exception.getCause().getClass());
    }
}