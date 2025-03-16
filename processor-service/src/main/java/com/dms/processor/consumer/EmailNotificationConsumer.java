package com.dms.processor.consumer;

import com.dms.processor.dto.EmailNotificationPayload;
import com.dms.processor.dto.NotificationEventRequest;
import com.dms.processor.enums.EventType;
import com.dms.processor.exception.InvalidDocumentException;
import com.dms.processor.service.AuthEmailService;
import com.dms.processor.service.DocumentEmailService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationConsumer {
    private final DocumentEmailService documentEmailService;
    private final AuthEmailService authEmailService;

    @RabbitListener(queues = "${rabbitmq.queues.email-document}")
    public void consumeNotificationEvent(NotificationEventRequest notificationEvent) {
        log.info("Consumed notification event: [Type: {}, Document: {}]",
                notificationEvent.getNotificationType(),
                notificationEvent.getDocumentId());

        try {
            if (notificationEvent.getSubject().equals(EventType.FAVORITE_NOTIFICATION.name())) {
                documentEmailService.sendNotifyForRelatedUserInDocument(notificationEvent);
            } else if (notificationEvent.getSubject().equals(EventType.COMMENT_REPORT_PROCESS_EVENT.name())) {
                // Send mail to commenter and reporters of the comment
                documentEmailService.sendCommentReportProcessNotification(notificationEvent);
            }
        } catch (Exception e) {
            log.error("Failed to send notification emails for document: {}", notificationEvent.getDocumentId(), e);
            throw e; // Retry will be handled by RabbitMQ configuration
        }
    }

    @RabbitListener(queues = "${rabbitmq.queues.email-auth}")
    public void consumeAuthEmail(EmailNotificationPayload payload) throws MessagingException {
        log.info("Received email request for user: {}", payload.getUsername());
        try {
            if (payload.getEventType().equals("VERIFY_OTP")) {
                authEmailService.sendOtpEmail(
                        payload.getTo(),
                        payload.getUsername(),
                        payload.getOtp(),
                        payload.getExpiryMinutes(),
                        payload.getMaxAttempts()
                );
                log.info("Successfully sent OTP email to: {}", payload.getTo());
            } else if (payload.getEventType().equals("PASSWORD_RESET")) {
                authEmailService.sendPasswordResetEmail(
                        payload.getTo(),
                        payload.getUsername(),
                        payload.getToken(),
                        payload.getExpiryMinutes()
                );
                log.info("Successfully sent password reset email to: {}", payload.getTo());
            }
        } catch (Exception e) {
            log.error("Failed to send email to: {}", payload.getTo(), e);
            throw e; // Retry will be handled by RabbitMQ configuration
        }
    }
}