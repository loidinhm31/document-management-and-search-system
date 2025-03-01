package com.dms.document.interaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CommentReportDetailResponse {
    private Long id;
    private String documentId;
    private Long commentId;
    private String commentContent;
    private UUID reporterUserId;
    private String reporterUsername;
    private UUID commentUserId;
    private String commentUsername;
    private String reportTypeCode;
    private String description;
    private boolean processed;
    private UUID resolvedBy;
    private String resolvedByUsername;
    private Instant createdAt;
    private Instant resolvedAt;
}