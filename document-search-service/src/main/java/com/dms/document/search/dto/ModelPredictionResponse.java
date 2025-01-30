package com.dms.document.search.dto;

import java.util.List;

public record ModelPredictionResponse(
        List<CategoryPrediction> predictions
) {}