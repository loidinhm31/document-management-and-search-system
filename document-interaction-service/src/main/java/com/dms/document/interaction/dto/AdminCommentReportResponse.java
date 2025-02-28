package com.dms.document.interaction.dto;

import com.dms.document.interaction.enums.CommentReportStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminCommentReportResponse(
        String documentId,
        Long commentId,
        String commentContent,
        UUID reporterUserId,
        String reporterUsername,
        UUID commentUserId,
        String commentUsername,
        String reportTypeCode,
        String description,
        boolean processed,
        CommentReportStatus status,
        UUID resolvedBy,
        String resolvedByUsername,
        Instant createdAt,
        Instant resolvedAt,
        int reportCount
) {}