package com.dms.document.interaction.controller;

import com.dms.document.interaction.dto.UpdateDocumentPreferencesRequest;
import com.dms.document.interaction.enums.InteractionType;
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
    private final DocumentPreferencesService preferencesService;

    @GetMapping
    public ResponseEntity<DocumentPreferences> getUserPreferences(
            @AuthenticationPrincipal Jwt jwt) {
        DocumentPreferences preferences = preferencesService.getDocumentPreferences(jwt.getSubject());
        return ResponseEntity.ok(preferences);
    }


    @PutMapping
    public ResponseEntity<DocumentPreferences> updatePreferences(
            @RequestBody UpdateDocumentPreferencesRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        DocumentPreferences updated = preferencesService.updateExplicitPreferences(
                jwt.getSubject(),
                request
        );
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/interactions")
    public ResponseEntity<Void> recordInteraction(
            @RequestParam String documentId,
            @RequestParam InteractionType type,
            @RequestParam(required = false) Long durationSeconds,
            @AuthenticationPrincipal Jwt jwt) {
        preferencesService.recordInteraction(
                jwt.getSubject(),
                documentId,
                type,
                durationSeconds
        );
        return ResponseEntity.ok().build();
    }

    @GetMapping("/content-weights")
    public ResponseEntity<Map<String, Double>> getContentTypeWeights(
            @AuthenticationPrincipal Jwt jwt) {
        Map<String, Double> weights = preferencesService.calculateContentTypeWeights(
                jwt.getSubject()
        );
        return ResponseEntity.ok(weights);
    }

    @GetMapping("/tags")
    public ResponseEntity<Set<String>> getRecommendedTags(
            @AuthenticationPrincipal Jwt jwt) {
        Set<String> tags = preferencesService.getRecommendedTags(jwt.getSubject());
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getInteractionStatistics(
            @AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> stats = preferencesService.getInteractionStatistics(
                jwt.getSubject()
        );
        return ResponseEntity.ok(stats);
    }
}