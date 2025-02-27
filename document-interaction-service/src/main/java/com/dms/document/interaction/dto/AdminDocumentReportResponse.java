package com.dms.document.interaction.dto;

import com.dms.document.interaction.enums.ReportStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminDocumentReportResponse(
        String documentId,
        String documentTitle,
        UUID documentOwnerId,
        String documentOwnerUsername,
        ReportStatus status,
        int reportCount,
        UUID resolvedBy,
        String resolvedByUsername,
        Instant resolvedAt,
        List<DocumentReportDetail> reportDetails
) {}