package com.dms.document.interaction.controller;

import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.service.DocumentBookmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class DocumentBookmarkController {
    private final DocumentBookmarkService documentBookmarkService;

    @PostMapping("/documents/{documentId}")
    public ResponseEntity<Void> bookmarkDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt) {
        documentBookmarkService.bookmarkDocument(documentId, jwt.getSubject());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> unbookmarkDocument(
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt) {
        documentBookmarkService.unbookmarkDocument(documentId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/{documentId}/status")
    public ResponseEntity<Boolean> isDocumentBookmarked(
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt) {
        boolean isBookmarked = documentBookmarkService.isDocumentBookmarked(documentId, jwt.getSubject());
        return ResponseEntity.ok(isBookmarked);
    }

    @GetMapping("/documents")
    public ResponseEntity<Page<DocumentInformation>> getBookmarkedDocuments(
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        Page<DocumentInformation> bookmarkedDocuments =
                documentBookmarkService.getBookmarkedDocuments(pageable, jwt.getSubject());
        return ResponseEntity.ok(bookmarkedDocuments);
    }
}