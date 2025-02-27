package com.dms.document.interaction.dto;

import com.dms.document.interaction.enums.ReportStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentReportDetail(
        Long reportId,
        UUID reporterUserId,
        String reporterUsername,
        String reportTypeCode,
        String description,
        ReportStatus status,
        Instant createdAt
) {}