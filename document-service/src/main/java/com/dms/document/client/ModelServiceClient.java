package com.dms.document.client;

import com.dms.document.config.ModelServiceFeignConfig;
import com.dms.document.dto.DocumentRequest;
import com.dms.document.dto.ModelPredictionResponse;
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