package com.example.demo2.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @NotBlank(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Role name is required")
    @Pattern(regexp = "^ROLE_[A-Z]+$", message = "Invalid role format")
    private String roleName;
}
