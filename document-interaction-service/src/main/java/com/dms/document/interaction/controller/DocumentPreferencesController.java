package com.dms.document.interaction.controller;

import com.dms.document.interaction.dto.UpdateDocumentPreferencesRequest;
import com.dms.document.interaction.model.DocumentPreferences;
import com.dms.document.interaction.service.DocumentPreferencesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
public class DocumentPreferencesController {
    private final DocumentPreferencesService documentPreferencesService;

    @GetMapping
    public ResponseEntity<DocumentPreferences> getDocumentPreferences(
            @AuthenticationPrincipal Jwt jwt) {
        DocumentPreferences preferences = documentPreferencesService.getDocumentPreferences(jwt.getSubject());
        return ResponseEntity.ok(preferences);
    }


    @PutMapping
    public ResponseEntity<DocumentPreferences> updatePreferences(
            @RequestBody UpdateDocumentPreferencesRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        DocumentPreferences updated = documentPreferencesService.updateExplicitPreferences(
                jwt.getSubject(),
                request
        );
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/content-weights")
    public ResponseEntity<Map<String, Double>> getContentTypeWeights(
            @AuthenticationPrincipal Jwt jwt) {
        Map<String, Double> weights = documentPreferencesService.getCalculateContentTypeWeights(
                jwt.getSubject()
        );
        return ResponseEntity.ok(weights);
    }

    @GetMapping("/tags")
    public ResponseEntity<Set<String>> getRecommendedTags(
            @AuthenticationPrincipal Jwt jwt) {
        Set<String> tags = documentPreferencesService.getRecommendedTags(jwt.getSubject());
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getInteractionStatistics(
            @AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> stats = documentPreferencesService.getInteractionStatistics(
                jwt.getSubject()
        );
        return ResponseEntity.ok(stats);
    }
}