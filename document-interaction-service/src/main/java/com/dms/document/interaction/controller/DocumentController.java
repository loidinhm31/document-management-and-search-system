package com.dms.document.interaction.controller;

import com.dms.document.interaction.dto.DocumentUpdateRequest;
import com.dms.document.interaction.dto.ShareSettings;
import com.dms.document.interaction.dto.UpdateShareSettingsRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.service.DocumentFavoriteService;
import com.dms.document.interaction.service.DocumentHistoryService;
import com.dms.document.interaction.service.DocumentService;
import com.dms.document.interaction.service.DocumentShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(BaseController.DOCUMENT_BASE_PATH)
@RequiredArgsConstructor
public class DocumentController extends BaseController {
    private final DocumentService documentService;
    private final DocumentFavoriteService documentFavoriteService;
    private final DocumentShareService documentShareService;
    private final DocumentHistoryService documentHistoryService;

    @PostMapping
    public ResponseEntity<DocumentInformation> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String summary,
            @RequestParam String courseCode,
            @RequestParam String major,
            @RequestParam String level,
            @RequestParam String category,
            @RequestParam(required = false) Set<String> tags,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        return ok(documentService.uploadDocument(
                file, summary, courseCode, major, level, category, tags, jwt.getSubject()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentInformation> getDocumentDetails(
            @PathVariable String id,
            @RequestParam(required = false) boolean history,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentService.getDocumentDetails(id, jwt.getSubject(), history));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentInformation> updateDocument(
            @PathVariable String id,
            @RequestBody DocumentUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentService.updateDocument(id, request, jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        documentService.deleteDocument(id, jwt.getSubject());
        return noContent();
    }

    @GetMapping("/tags/suggestions")
    public ResponseEntity<Set<String>> getTagSuggestions(
            @RequestParam(required = false) String prefix) {
        return ok(documentService.getPopularTags(prefix));
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    public ResponseEntity<byte[]> downloadDocumentVersion(
            @PathVariable String id,
            @PathVariable Integer versionNumber,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) boolean history,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        byte[] content = documentService.getDocumentVersionContent(id, versionNumber, jwt.getSubject(), action, history);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document\"")
                .body(content);
    }

    @PutMapping("/{id}/versions/{versionNumber}")
    public ResponseEntity<DocumentInformation> revertToVersion(
            @PathVariable String id,
            @PathVariable Integer versionNumber,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentService.revertToVersion(id, versionNumber, jwt.getSubject()));
    }

    @PostMapping("/{id}/favorites")
    public ResponseEntity<Void> favoriteDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        documentFavoriteService.favoriteDocument(id, jwt.getSubject());
        return ok();
    }

    @DeleteMapping("{id}/favorites")
    public ResponseEntity<Void> unfavoriteDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        documentFavoriteService.unfavoriteDocument(id, jwt.getSubject());
        return noContent();
    }

    @GetMapping("/{id}/favorites/status")
    public ResponseEntity<Boolean> isDocumentFavorited(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentFavoriteService.isDocumentFavorited(id, jwt.getSubject()));
    }

    @GetMapping("/favorites")
    public ResponseEntity<Page<DocumentInformation>> getFavoritedDocuments(
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentFavoriteService.getFavoritedDocuments(pageable, jwt.getSubject()));
    }

    @GetMapping("/{id}/sharing")
    public ResponseEntity<ShareSettings> getDocumentShareSettings(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentShareService.getDocumentShareSettings(id, jwt.getSubject()));
    }

    @PutMapping("/{id}/sharing")
    public ResponseEntity<DocumentInformation> updateDocumentShareSettings(
            @PathVariable String id,
            @RequestBody UpdateShareSettingsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentShareService.updateDocumentShareSettings(id, request, jwt.getSubject()));
    }

    @GetMapping("/sharing/users")
    public ResponseEntity<List<UserResponse>> searchShareableUsers(
            @RequestParam String query) {
        return ok(documentShareService.searchShareableUsers(query));
    }

    @PostMapping("/sharing/users/details")
    public ResponseEntity<List<UserResponse>> getShareableUserDetails(
            @RequestBody List<UUID> userIds) {
        return ok(documentShareService.getShareableUserDetails(userIds));
    }

    @GetMapping("/{id}/statistics/downloads")
    public ResponseEntity<Map<String, Integer>> getDocumentDownloadStatistics(@PathVariable String id) {
        int downloadCount = documentHistoryService.getDocumentDownloadCount(id);
        return ok(Map.of("downloadCount", downloadCount));
    }
}