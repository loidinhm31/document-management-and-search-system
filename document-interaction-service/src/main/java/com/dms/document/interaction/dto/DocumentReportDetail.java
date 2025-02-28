package com.dms.document.interaction.dto;

import com.dms.document.interaction.enums.DocumentReportStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentReportDetail(
        Long reportId,
        UUID reporterUserId,
        String reporterUsername,
        String reportTypeCode,
        String description,
        DocumentReportStatus status,
        Instant createdAt
) {}