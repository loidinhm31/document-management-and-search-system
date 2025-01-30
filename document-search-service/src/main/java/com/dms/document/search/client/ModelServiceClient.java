package com.dms.document.search.client;

import com.dms.document.search.config.ModelServiceFeignConfig;
import com.dms.document.search.dto.DocumentRequest;
import com.dms.document.search.dto.ModelPredictionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "model-service",
        url = "${model-service.url}",
        configuration = ModelServiceFeignConfig.class
)
public interface ModelServiceClient {
    @PostMapping("/predict")
    ModelPredictionResponse getPrediction(@RequestBody DocumentRequest request);
}