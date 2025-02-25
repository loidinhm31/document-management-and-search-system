package com.dms.document.interaction.dto;


import com.dms.document.interaction.enums.AppRole;

import java.util.UUID;

public record RoleResponse(UUID roleId, AppRole roleName) {
}
