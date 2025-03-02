package com.dms.document.interaction.dto;

import java.util.Set;

public record DocumentUpdateRequest(
        String summary,
        Set<String> courseCodes,
        Set<String> majors,
        String level,
        Set<String> categories,
        Set<String> tags
) {
}