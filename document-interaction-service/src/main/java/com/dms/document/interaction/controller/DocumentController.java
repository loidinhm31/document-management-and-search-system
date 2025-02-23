package com.dms.document.interaction.controller;

import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentVersion;
import com.dms.document.interaction.service.DocumentFavoriteService;
import com.dms.document.interaction.service.DocumentHistoryService;
import com.dms.document.interaction.service.DocumentService;
import com.dms.document.interaction.service.DocumentShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(BaseController.DOCUMENT_BASE_PATH)
@RequiredArgsConstructor
@Tag(name = "Document Operations", description = "APIs for core document operations")
public class DocumentController extends BaseController {
    private final DocumentService documentService;
    private final DocumentFavoriteService documentFavoriteService;
    private final DocumentShareService documentShareService;
    private final DocumentHistoryService documentHistoryService;

    @Operation(summary = "Upload a new document",
            description = "Upload a document file with metadata like summary, course code, major, etc.")
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

    @Operation(summary = "Get document details",
            description = "Retrieve detailed information about a specific document")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentInformation> getDocumentDetails(
            @PathVariable String id,
            @RequestParam(required = false) boolean history,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentService.getDocumentDetails(id, jwt.getSubject(), history));
    }

    @Operation(
            summary = "Download document content",
            description = "Download the current version of a document's content as a byte array. " +
                    "Optionally records the download action in user history."
    )
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable String id,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) boolean history,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        byte[] content = documentService.getDocumentContent(id, jwt.getSubject(), action, history);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document\"")
                .body(content);
    }

    @Operation(
            summary = "Get document thumbnail",
            description = "Retrieve the thumbnail image for a document. Returns a placeholder if the thumbnail is not yet available or if processing failed. " +
                    "Supports ETag-based caching for efficient retrieval."
    )
    @GetMapping("/{id}/thumbnails")
    public ResponseEntity<byte[]> getDocumentThumbnail(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) throws IOException {

        String username = jwt.getSubject();
        DocumentInformation document = documentService.getDocumentDetails(id, username, false);
        ThumbnailResponse thumbnailResponse = documentService.getDocumentThumbnail(id, username);

        HttpHeaders headers = new HttpHeaders();

        // For non-placeholder (actual) thumbnails, use normal caching
        if (!thumbnailResponse.isPlaceholder()) {
            // Generate ETag considering both document update time and version info
            String eTag = generateETag(document);

            // Check if client's cached version is still valid
            if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(eTag)
                        .build();
            }

            headers.setETag(eTag);
            headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS)
                    .mustRevalidate() // Force revalidation
                    .noTransform());

            // Add version header for cache busting
            headers.set("X-Document-Version", String.valueOf(document.getCurrentVersion()));
        } else {
            // For placeholders, prevent caching
            headers.setCacheControl(CacheControl.noCache());

            // If it's a processing placeholder, add retry-after header
            if (thumbnailResponse.getRetryAfterSeconds() != null) {
                headers.set("Retry-After", String.valueOf(thumbnailResponse.getRetryAfterSeconds()));
            }
        }

        return ResponseEntity.status(thumbnailResponse.getStatus())
                .headers(headers)
                .contentType(MediaType.IMAGE_PNG)
                .body(thumbnailResponse.getData());
    }

    @Operation(
            summary = "Update document with new file",
            description = "Update an existing document by uploading a new file and optionally updating its metadata. " +
                    "Creates a new version of the document and triggers reprocessing."
    )
    @PutMapping(value = "/{id}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentInformation> updateDocumentWithFile(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String courseCode,
            @RequestParam String major,
            @RequestParam String level,
            @RequestParam String category,
            @RequestParam(required = false) Set<String> tags,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        DocumentUpdateRequest updateRequest = new DocumentUpdateRequest(
                summary, courseCode, major, level, category, tags
        );
        return ok(documentService.updateDocumentWithFile(id, file, updateRequest, jwt.getSubject()));
    }

    @Operation(summary = "Update document metadata",
            description = "Update document metadata like summary, course code, major, etc.")
    @PutMapping("/{id}")
    public ResponseEntity<DocumentInformation> updateDocument(
            @PathVariable String id,
            @RequestBody DocumentUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentService.updateDocument(id, request, jwt.getSubject()));
    }

    @Operation(summary = "Delete document",
            description = "Soft delete a document and its associated data")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        documentService.deleteDocument(id, jwt.getSubject());
        return noContent();
    }

    @Operation(summary = "Get tag suggestions",
            description = "Retrieve popular tags, optionally filtered by prefix")
    @GetMapping("/tags/suggestions")
    public ResponseEntity<Set<String>> getTagSuggestions(
            @RequestParam(required = false) String prefix) {
        return ok(documentService.getPopularTags(prefix));
    }

    @Operation(summary = "Download specific document version",
            description = "Download document content for a specific version number")
    @GetMapping("/{id}/versions/{versionNumber}/content")
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

    @Operation(summary = "Revert to previous version",
            description = "Revert document to a specific previous version")
    @PutMapping("/{id}/versions/{versionNumber}")
    public ResponseEntity<DocumentInformation> revertToVersion(
            @PathVariable String id,
            @PathVariable Integer versionNumber,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentService.revertToVersion(id, versionNumber, jwt.getSubject()));
    }

    @Operation(summary = "Add document to favorites",
            description = "Mark a document as favorite for the current user")
    @PostMapping("/{id}/favorites")
    public ResponseEntity<Void> favoriteDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        documentFavoriteService.favoriteDocument(id, jwt.getSubject());
        return ok();
    }

    @Operation(summary = "Remove document from favorites",
            description = "Remove a document from user's favorites")
    @DeleteMapping("{id}/favorites")
    public ResponseEntity<Void> unfavoriteDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        documentFavoriteService.unfavoriteDocument(id, jwt.getSubject());
        return noContent();
    }

    @Operation(summary = "Check favorite status",
            description = "Check if a document is in user's favorites")
    @GetMapping("/{id}/favorites/status")
    public ResponseEntity<Boolean> isDocumentFavorited(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentFavoriteService.isDocumentFavorited(id, jwt.getSubject()));
    }

    @Operation(summary = "Get favorited documents",
            description = "Retrieve paginated list of user's favorite documents")
    @GetMapping("/favorites")
    public ResponseEntity<Page<DocumentInformation>> getFavoritedDocuments(
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentFavoriteService.getFavoritedDocuments(pageable, jwt.getSubject()));
    }

    @Operation(summary = "Get document share settings",
            description = "Retrieve document sharing configuration")
    @GetMapping("/{id}/sharing")
    public ResponseEntity<ShareSettings> getDocumentShareSettings(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentShareService.getDocumentShareSettings(id, jwt.getSubject()));
    }

    @Operation(summary = "Update share settings",
            description = "Update document sharing configuration including public access and specific users")
    @PutMapping("/{id}/sharing")
    public ResponseEntity<DocumentInformation> updateDocumentShareSettings(
            @PathVariable String id,
            @RequestBody UpdateShareSettingsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ok(documentShareService.updateDocumentShareSettings(id, request, jwt.getSubject()));
    }

    @Operation(summary = "Search users for sharing",
            description = "Search users that document can be shared with")
    @GetMapping("/sharing/users")
    public ResponseEntity<List<UserResponse>> searchShareableUsers(
            @RequestParam String query) {
        return ok(documentShareService.searchShareableUsers(query));
    }

    @Operation(summary = "Get shared users details",
            description = "Get detailed information about users document is shared with")
    @PostMapping("/sharing/users/details")
    public ResponseEntity<List<UserResponse>> getShareableUserDetails(
            @RequestBody List<UUID> userIds) {
        return ok(documentShareService.getShareableUserDetails(userIds));
    }

    @Operation(summary = "Get download statistics",
            description = "Retrieve document download count statistics")
    @GetMapping("/{id}/statistics/downloads")
    public ResponseEntity<Map<String, Integer>> getDocumentDownloadStatistics(@PathVariable String id) {
        int downloadCount = documentHistoryService.getDocumentDownloadCount(id);
        return ok(Map.of("downloadCount", downloadCount));
    }

    private String generateETag(DocumentInformation document) {
        // Include the current version number and specific version's thumbnail path
        String contentKey = String.format("%s_%s_%s_%s_%s_%s",
                document.getId(),
                document.getUpdatedAt().toEpochMilli(),
                document.getCurrentVersion(),
                document.getFileSize(),
                document.getFilename(),
                // Include version-specific thumbnail path or "none"
                document.getLatestVersion()
                        .map(DocumentVersion::getThumbnailPath)
                        .orElse("none")
        );

        return String.format("\"%s\"", DigestUtils.md5DigestAsHex(contentKey.getBytes()));
    }
}