package com.dms.document.interaction.controller;

import com.dms.document.interaction.dto.ShareSettings;
import com.dms.document.interaction.dto.UpdateShareSettingsRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.service.DocumentShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shares")
@RequiredArgsConstructor
public class DocumentShareController {
    private final DocumentShareService documentShareService;

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<ShareSettings> getDocumentShareSettings(
            @PathVariable String documentId,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        ShareSettings settings = documentShareService.getDocumentShareSettings(documentId, username);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/documents/{documentId}")
    public ResponseEntity<DocumentInformation> updateDocumentShareSettings(
            @PathVariable String documentId,
            @RequestBody UpdateShareSettingsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        DocumentInformation document = documentShareService.updateDocumentShareSettings(documentId, request, username);
        return ResponseEntity.ok(document);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> searchShareableUsers(
            @RequestParam String query) {
        return ResponseEntity.ok(documentShareService.searchShareableUsers(query));
    }

    @PostMapping("/users/details")
    public ResponseEntity<List<UserResponse>> getShareableUserDetails(
            @RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(documentShareService.getShareableUserDetails(userIds));
    }
}