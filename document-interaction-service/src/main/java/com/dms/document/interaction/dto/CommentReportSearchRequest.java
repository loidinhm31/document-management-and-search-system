package com.dms.document.interaction.dto;

import java.time.Instant;

public record CommentReportSearchRequest(
        String commentContent,
        String reportTypeCode,
        Instant createdFrom,
        Instant createdTo,
        Boolean resolved
) {}
