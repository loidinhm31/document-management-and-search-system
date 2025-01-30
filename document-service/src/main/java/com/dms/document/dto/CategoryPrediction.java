package com.dms.document.dto;

public record CategoryPrediction(
        String category,
        double confidence
) {}