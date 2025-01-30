package com.dms.document.interaction.dto;

public record CategoryPrediction(
        String category,
        double confidence
) {}