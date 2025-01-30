package com.dms.document.dto;

import java.util.List;

public record ModelPredictionResponse(
        List<CategoryPrediction> predictions
) {}