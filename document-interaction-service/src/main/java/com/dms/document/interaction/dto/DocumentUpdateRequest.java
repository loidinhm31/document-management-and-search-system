package com.dms.document.interaction.dto;

import java.util.Set;

public record DocumentUpdateRequest(
        String summary,
        String courseCode,
        String major,
        String level,
        String category,
        Set<String> tags
) {
}
