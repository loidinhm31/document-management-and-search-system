package com.dms.document.interaction.controller;

import com.dms.document.interaction.dto.UpdateDocumentPreferencesRequest;
import com.dms.document.interaction.model.DocumentPreferences;
import com.dms.document.interaction.service.DocumentPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Document Preferences", description = "APIs for managing user document preferences")
public class DocumentPreferencesController extends BaseController {
    private final DocumentPreferencesService documentPreferencesService;

    @Operation(summary = "Get user document preferences",
            description = "Retrieve user's document preferences including preferred categories, tags, etc.")
    @GetMapping
    public ResponseEntity<DocumentPreferences> getDocumentPreferences(
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentPreferencesService.getDocumentPreferences(jwt.getSubject()));
    }

    @Operation(summary = "Update document preferences",
            description = "Update user's explicit document preferences")    @PutMapping
    public ResponseEntity<DocumentPreferences> updatePreferences(
            @RequestBody UpdateDocumentPreferencesRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentPreferencesService.updateExplicitPreferences(jwt.getSubject(), request));
    }

    @Operation(summary = "Get content type weights",
            description = "Calculate and retrieve weights for different content types based on user interactions")
    @GetMapping("/content-weights")
    public ResponseEntity<Map<String, Double>> getContentTypeWeights(
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentPreferencesService.getCalculateContentTypeWeights(jwt.getSubject()));
    }

    @Operation(summary = "Get recommended tags",
            description = "Get personalized tag recommendations based on user interactions")
    @GetMapping("/tags")
    public ResponseEntity<Set<String>> getRecommendedTags(
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentPreferencesService.getRecommendedTags(jwt.getSubject()));
    }


    @Operation(summary = "Get interaction statistics",
            description = "Retrieve statistics about user's document interactions")
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getInteractionStatistics(
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentPreferencesService.getInteractionStatistics(jwt.getSubject()));
    }
}