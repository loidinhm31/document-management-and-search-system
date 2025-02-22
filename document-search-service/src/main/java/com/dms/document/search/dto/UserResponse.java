package com.dms.document.search.dto;

import java.util.UUID;

public record UserResponse(
        UUID userId,
        String username,
        String email,
        RoleResponse role
) {
}