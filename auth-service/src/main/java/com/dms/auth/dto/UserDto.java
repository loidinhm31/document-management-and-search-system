package com.dms.auth.dto;

import com.dms.auth.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID userId;
    private String username;
    private String email;
    private boolean accountNonLocked;
    private boolean enabled;
    private String twoFactorSecret;
    private boolean isTwoFactorEnabled;
    private String signUpMethod;
    private RoleDto role;
    private Instant createdDate;
    private Instant updatedDate;
}
