package com.dms.document.dto;

public record DocumentRequest(
        String text,
        String filename,
        String language
) {}