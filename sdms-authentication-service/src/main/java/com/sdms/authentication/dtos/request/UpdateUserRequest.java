package com.sdms.authentication.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;


@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UpdateUserRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Role name is required")
    @Pattern(regexp = "^ROLE_[A-Z]+$", message = "Invalid role format")
    private String roleName;
}
