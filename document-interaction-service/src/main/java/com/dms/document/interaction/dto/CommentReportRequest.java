package com.dms.document.interaction.dto;

public record CommentReportRequest(
        String reportTypeCode,
        String description
) {}