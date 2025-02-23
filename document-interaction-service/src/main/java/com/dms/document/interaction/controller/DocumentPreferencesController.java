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
@RequestMapping(BaseController.DOCUMENT_BASE_PATH + "/preferences")
@RequiredArgsConstructor
public class DocumentPreferencesController extends BaseController {
    private final DocumentPreferencesService documentPreferencesService;

    @GetMapping
    public ResponseEntity<DocumentPreferences> getDocumentPreferences(
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentPreferencesService.getDocumentPreferences(jwt.getSubject()));
    }

    @PutMapping
    public ResponseEntity<DocumentPreferences> updatePreferences(
            @RequestBody UpdateDocumentPreferencesRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentPreferencesService.updateExplicitPreferences(jwt.getSubject(), request));
    }

    @GetMapping("/content-weights")
    public ResponseEntity<Map<String, Double>> getContentTypeWeights(
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentPreferencesService.getCalculateContentTypeWeights(jwt.getSubject()));
    }

    @GetMapping("/tags")
    public ResponseEntity<Set<String>> getRecommendedTags(
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentPreferencesService.getRecommendedTags(jwt.getSubject()));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getInteractionStatistics(
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentPreferencesService.getInteractionStatistics(jwt.getSubject()));
    }
}