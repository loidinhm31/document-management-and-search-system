package com.dms.processor.dto;

import lombok.Data;

@Data
public class PasswordResetEmailRequest {
    private String to;
    private String username;
    private String token;
    private int expiryHours;
}