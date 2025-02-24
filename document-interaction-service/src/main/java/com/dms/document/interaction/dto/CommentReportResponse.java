package com.dms.document.interaction.dto;

import java.time.Instant;

public record CommentReportResponse(
        Long id,
        String documentId,
        Long commentId,
        String reportTypeCode,
        TranslationDTO reportTypeTranslation,
        String description,
        boolean resolved,
        Instant createdAt
) {}
