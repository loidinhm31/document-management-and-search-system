package com.dms.document.interaction.client;


import com.dms.document.interaction.config.ModelServiceFeignConfig;
import com.dms.document.interaction.dto.DocumentRequest;
import com.dms.document.interaction.dto.ModelPredictionResponse;
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