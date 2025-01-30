package com.dms.document.service;

import com.dms.document.client.ModelServiceClient;
import com.dms.document.dto.DocumentRequest;
import com.dms.document.dto.ModelPredictionResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelPredictionService {
    private final ModelServiceClient modelServiceClient;

    @CircuitBreaker(name = "modelService", fallbackMethod = "getDefaultPrediction")
    public ModelPredictionResponse getPredictionForDocument(String text, String filename, String language) {
        try {
            DocumentRequest request = new DocumentRequest(text, filename, language);
            return modelServiceClient.getPrediction(request);
        } catch (Exception e) {
            log.error("Error getting prediction from model service", e);
            throw e;
        }
    }

}