package com.dms.document.search.dto;



import com.dms.document.search.enums.AppRole;

import java.util.UUID;

public record RoleResponse(UUID roleId, AppRole roleName) {
}
