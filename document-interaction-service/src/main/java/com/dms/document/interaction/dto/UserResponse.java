package com.dms.document.interaction.dto;

import java.util.UUID;

public record UserResponse(
        UUID userId,
        String username,
        String email,
        RoleResponse role
) {}