package com.dms.document.search.dto;

public record DocumentRequest(
        String text,
        String filename,
        String language
) {}