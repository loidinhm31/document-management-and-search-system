package com.sdms.auth.dtos.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSecurityRequest {
    private String currentPassword;

    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$",
            message = "Password must contain at least one digit, one lowercase, one uppercase, and one special character")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String newPassword;

    private Boolean enable2FA;
    private String twoFactorCode;
}