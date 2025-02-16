package com.dms.document.interaction.controller;

import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.service.DocumentFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class DocumentFavoriteController {
    private final DocumentFavoriteService documentFavoriteService;

    @PostMapping("/documents/{documentId}")
    public ResponseEntity<Void> favoriteDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt) {
        documentFavoriteService.favoriteDocument(documentId, jwt.getSubject());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> unfavoriteDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt) {
        documentFavoriteService.unfavoriteDocument(documentId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/{documentId}/status")
    public ResponseEntity<Boolean> isDocumentFavorited(
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt) {
        boolean isFavorited = documentFavoriteService.isDocumentFavorited(documentId, jwt.getSubject());
        return ResponseEntity.ok(isFavorited);
    }

    @GetMapping("/documents")
    public ResponseEntity<Page<DocumentInformation>> getFavoritedDocuments(
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        Page<DocumentInformation> favoritedDocuments =
                documentFavoriteService.getFavoritedDocuments(pageable, jwt.getSubject());
        return ResponseEntity.ok(favoritedDocuments);
    }
}