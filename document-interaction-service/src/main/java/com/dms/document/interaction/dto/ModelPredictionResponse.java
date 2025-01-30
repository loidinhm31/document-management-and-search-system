package com.dms.document.interaction.dto;

import java.util.List;

public record ModelPredictionResponse(
        List<CategoryPrediction> predictions
) {}