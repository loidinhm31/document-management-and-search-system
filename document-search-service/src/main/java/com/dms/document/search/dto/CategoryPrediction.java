package com.dms.document.search.dto;

public record CategoryPrediction(
        String category,
        double confidence
) {}