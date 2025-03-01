package com.dms.document.interaction.dto;

import com.dms.document.interaction.enums.DocumentReportStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminDocumentReportResponse(
        String documentId,
        String documentTitle,
        UUID documentOwnerId,
        String documentOwnerUsername,
        DocumentReportStatus status,
        boolean processed,
        int reportCount,
        UUID resolvedBy,
        String resolvedByUsername,
        Instant resolvedAt
) {}