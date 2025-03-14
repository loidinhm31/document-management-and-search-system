package com.dms.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username or email is required")
    @Size(min = 3, max = 100, message = "Login must be between 3 and 100 characters")
    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;
}