package com.dms.auth.dto.request;

public record UserSearchRequest(
        String search,
        Boolean enabled,
        String role,
        String sortField,
        String sortDirection,
        int page,
        int size
) {
}
