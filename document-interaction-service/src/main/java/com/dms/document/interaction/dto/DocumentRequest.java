package com.dms.document.interaction.dto;

public record DocumentRequest(
        String text,
        String filename,
        String language
) {}