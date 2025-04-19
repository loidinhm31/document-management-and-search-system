package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.dto.DocumentFavoriteCheck;
import com.dms.document.interaction.service.DocumentFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstant.API_VERSION + ApiConstant.DOCUMENT_BASE_PATH + ApiConstant.DOCUMENT_ID_PATH + "/favorites")
@RequiredArgsConstructor
@Tag(name = "Document Favorites", description = "APIs for managing document favorites")
public class DocumentFavoriteController {
    private final DocumentFavoriteService documentFavoriteService;

    @Operation(summary = "Add/ Remove document to favorites",
            description = "Mark/ Remove a document as favorite for the current user")
    @PostMapping
    public ResponseEntity<Void> favoriteDocument(
            @PathVariable String id,
            @RequestParam(required = false) boolean favorite,
            @AuthenticationPrincipal Jwt jwt) {
        documentFavoriteService.favoriteDocument(id, favorite, jwt.getSubject());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Check favorite status",
            description = "Check if a document is in user's favorites")
    @GetMapping("/status")
    public ResponseEntity<DocumentFavoriteCheck> isDocumentFavorited(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentFavoriteService.checkDocumentFavorited(id, jwt.getSubject()));
    }
}
