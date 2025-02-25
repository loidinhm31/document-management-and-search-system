package com.dms.processor.consumer;

import com.dms.processor.dto.OtpEmailRequest;
import com.dms.processor.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OtpEmailConsumer {
    private final EmailService emailService;

    @RabbitListener(queues = "${rabbitmq.queues.otp}")
    public void consumeOtpEmail(OtpEmailRequest emailRequest) {
        log.info("Received OTP email request for user: {}", emailRequest.getUsername());
        try {
            emailService.sendOtpEmail(
                    emailRequest.getTo(),
                    emailRequest.getUsername(),
                    emailRequest.getOtp(),
                    emailRequest.getExpiryMinutes(),
                    emailRequest.getMaxAttempts()
            );
            log.info("Successfully sent OTP email to: {}", emailRequest.getTo());
        } catch (Exception e) {
            log.error("Failed to send OTP email to: {}", emailRequest.getTo(), e);
            throw e; // Retry will be handled by RabbitMQ configuration
        }
    }
}