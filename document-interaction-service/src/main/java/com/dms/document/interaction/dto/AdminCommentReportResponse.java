package com.dms.document.interaction.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminCommentReportResponse(
        Long id,
        String documentId,
        Long commentId,
        String commentContent,
        UUID reporterUserId,
        String reporterUsername,
        UUID commentUserId,
        String commentUsername,
        String reportTypeCode,
        String description,
        boolean resolved,
        UUID resolvedBy,
        String resolvedByUsername,
        Instant createdAt,
        Instant resolvedAt
) {}