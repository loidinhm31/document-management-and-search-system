package com.dms.processor.dto;

import lombok.Data;

@Data
public class OtpEmailRequest {
    private String to;
    private String username;
    private String otp;
    private int expiryMinutes;
    private int maxAttempts;
}