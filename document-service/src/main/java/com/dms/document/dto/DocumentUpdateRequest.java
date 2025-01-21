package com.dms.document.dto;

import java.util.Set;

public record DocumentUpdateRequest(
        String courseCode,
        String major,
        String level,
        String category,
        Set<String> tags
) {
}
