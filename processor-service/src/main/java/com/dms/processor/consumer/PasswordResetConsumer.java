package com.dms.processor.consumer;

import com.dms.processor.dto.PasswordResetEmailRequest;
import com.dms.processor.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PasswordResetConsumer {

    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    @RabbitListener(queues = "${rabbitmq.queues.password-reset}")
    public void consumePasswordReset(PasswordResetEmailRequest request) {
        log.info("Received password reset request for user: {}", request.getUsername());
        try {
            String resetUrl = String.format("%s/reset-password?token=%s", baseUrl, request.getToken());
            emailService.sendPasswordResetEmail(
                    request.getTo(),
                    request.getUsername(),
                    resetUrl,
                    request.getExpiryHours()
            );
            log.info("Successfully sent password reset email to: {}", request.getTo());
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", request.getTo(), e);
            throw e; // Retry will be handled by RabbitMQ configuration
        }
    }
}