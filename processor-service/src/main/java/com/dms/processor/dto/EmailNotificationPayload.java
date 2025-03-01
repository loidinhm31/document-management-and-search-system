package com.dms.processor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailNotificationPayload {
    private String to;
    private String username;
    private String otp;
    private int expiryMinutes;
    private int maxAttempts;
    private String subject;
    private String templateName;
    private String token;
    private String eventType;
}