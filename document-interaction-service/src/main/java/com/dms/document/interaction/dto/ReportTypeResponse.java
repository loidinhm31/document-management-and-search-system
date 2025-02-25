package com.dms.document.interaction.dto;

public record ReportTypeResponse(
        String code,
        TranslationDTO translations,
        String description
) {}