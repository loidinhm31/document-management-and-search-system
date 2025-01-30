package com.dms.document.controller;

import com.dms.document.dto.ModelPredictionResponse;
import com.dms.document.service.ModelPredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prediction")
@RequiredArgsConstructor
public class PredictionController {

    private final ModelPredictionService modelPredictionService;

    @PostMapping("/classification")
    public ResponseEntity<ModelPredictionResponse> getManualPrediction(
            @RequestParam String text,
            @RequestParam String filename,
            @RequestParam(required = false, defaultValue = "en") String language) {

        ModelPredictionResponse prediction = modelPredictionService.getPredictionForDocument(
                text,
                filename,
                language
        );

        return ResponseEntity.ok(prediction);
    }
}