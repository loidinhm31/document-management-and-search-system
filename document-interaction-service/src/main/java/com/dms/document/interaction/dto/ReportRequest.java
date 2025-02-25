package com.dms.document.interaction.dto;

public record ReportRequest(
        String reportTypeCode,
        String description
) {
}