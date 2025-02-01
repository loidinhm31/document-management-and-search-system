package com.dms.document.interaction.controller;

import com.dms.document.interaction.dto.DocumentVersionResponse;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/documents/{documentId}/versions")
@RequiredArgsConstructor
public class DocumentVersionController {
    private final DocumentService documentService;

    @GetMapping("/{versionNumber}")
    public ResponseEntity<DocumentInformation> getDocumentVersion(
            @PathVariable String documentId,
            @PathVariable Integer versionNumber,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        DocumentInformation document = documentService.getDocumentVersion(documentId, versionNumber, username);
        return ResponseEntity.ok(document);
    }

    @GetMapping("/{versionNumber}/download")
    public ResponseEntity<byte[]> downloadDocumentVersion(
            @PathVariable String documentId,
            @PathVariable Integer versionNumber,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        String username = jwt.getSubject();
        byte[] content = documentService.getDocumentVersionContent(documentId, versionNumber, username);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"document\"")
                .body(content);
    }

    @GetMapping
    public ResponseEntity<DocumentVersionResponse> getVersionHistory(
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        DocumentVersionResponse response = documentService.getVersionHistory(documentId, username);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{versionNumber}/revert")
    public ResponseEntity<DocumentInformation> revertToVersion(
            @PathVariable String documentId,
            @PathVariable Integer versionNumber,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        DocumentInformation document = documentService.revertToVersion(documentId, versionNumber, username);
        return ResponseEntity.ok(document);
    }
}