package com.dms.document.interaction.dto;

import java.time.Instant;

public record ReportResponse(
        Long id,
        String documentId,
        String reportTypeCode,
        TranslationDTO reportTypeTranslation,
        String description,
        Instant createdAt
) {
}