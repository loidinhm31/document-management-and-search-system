package com.dms.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PasswordResetEmailPayload {
    private String to;
    private String username;
    private String token;
    private int expiryHours;
}