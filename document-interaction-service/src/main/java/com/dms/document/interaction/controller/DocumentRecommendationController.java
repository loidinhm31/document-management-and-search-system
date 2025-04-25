package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.service.DocumentRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstant.API_VERSION + ApiConstant.DOCUMENT_BASE_PATH + ApiConstant.DOCUMENT_ID_PATH + "/recommendations")
@RequiredArgsConstructor
@Tag(name = "Document Recommendations", description = "APIs for managing document recommendations by mentors")
public class DocumentRecommendationController {
    private final DocumentRecommendationService documentRecommendationService;

    @Operation(summary = "Recommend/ Unrecommend document",
            description = "Mark/ Remove a document as recommended by a mentor")
    @PostMapping
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<Void> recommendDocument(
            @PathVariable String id,
            @RequestParam(required = false) boolean recommend,
            @AuthenticationPrincipal Jwt jwt) {
        boolean recommended = documentRecommendationService.recommendDocument(id, recommend, jwt.getSubject());
        return recommended ? ResponseEntity.ok().build() : ResponseEntity.noContent().build();
    }

    @Operation(summary = "Check recommendation status",
            description = "Check if a document is recommended by the current mentor")
    @GetMapping("/status")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<Boolean> isDocumentRecommended(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentRecommendationService.isDocumentRecommendedByUser(id, jwt.getSubject()));
    }
}