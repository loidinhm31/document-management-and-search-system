package com.dms.auth.dto;


import com.dms.auth.enums.AppRole;

import java.util.UUID;

public record RoleDto(UUID roleId, AppRole roleName) {
}
