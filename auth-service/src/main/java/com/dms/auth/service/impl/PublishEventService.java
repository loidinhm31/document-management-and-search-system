package com.dms.auth.service.impl;

import com.dms.auth.dto.EmailNotificationPayload;
import com.dms.auth.entity.User;
import com.dms.auth.producer.RabbitMQMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublishEventService {
    private final RabbitMQMessageProducer rabbitMQMessageProducer;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${rabbitmq.exchanges.notification}")
    private String notificationExchange;

    @Value("${rabbitmq.routing-keys.otp}")
    private String otpRoutingKey;

    public void sendOtpEmail(User user, String otp) {
        try {
            EmailNotificationPayload payload = EmailNotificationPayload.builder()
                    .to(user.getEmail())
                    .username(user.getUsername())
                    .otp(otp)
                    .expiryMinutes(otpExpiryMinutes)
                    .maxAttempts(maxAttempts)
                    .build();

            log.info("Publishing OTP email for user: {}", user.getUsername());
            rabbitMQMessageProducer.publish(
                    payload,
                    notificationExchange,
                    otpRoutingKey
            );
        } catch (Exception e) {
            log.error("Failed to publish OTP email", e);
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }
}