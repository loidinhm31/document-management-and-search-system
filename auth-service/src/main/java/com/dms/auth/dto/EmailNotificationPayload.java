package com.dms.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmailNotificationPayload {
    private String to;
    private String username;
    private String otp;
    private int expiryMinutes;
    private int maxAttempts;
    private String token;
    private String eventType;
    private String templateName;
}