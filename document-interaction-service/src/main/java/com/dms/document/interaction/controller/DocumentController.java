package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentVersion;
import com.dms.document.interaction.service.DocumentHistoryService;
import com.dms.document.interaction.service.DocumentService;
import com.dms.document.interaction.service.DocumentShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(ApiConstant.API_VERSION + ApiConstant.DOCUMENT_BASE_PATH)
@RequiredArgsConstructor
@Tag(name = "Document Operations", description = "APIs for core document operations")
public class DocumentController {
    private final DocumentService documentService;
    private final DocumentShareService documentShareService;
    private final DocumentHistoryService documentHistoryService;

    @Operation(summary = "Upload a new document",
            description = "Upload a document file with metadata like summary, course codes, majors, etc.")
    @PostMapping
    public ResponseEntity<DocumentInformation> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) Set<String> courseCodes,
            @RequestParam(required = false) Set<String> majors,
            @RequestParam String level,
            @RequestParam Set<String> categories,
            @RequestParam(required = false) Set<String> tags,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        return ResponseEntity.ok(documentService.uploadDocument(
                file, summary, courseCodes, majors, level, categories, tags, jwt.getSubject()));
    }

    @Operation(summary = "Get document details",
            description = "Retrieve detailed information about a specific document")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentInformation> getDocumentDetails(
            @PathVariable String id,
            @RequestParam(required = false) boolean history,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentService.getDocumentDetails(id, jwt.getSubject(), history));
    }

    @Operation(
            summary = "Download document file",
            description = "Download the current version of a document's content as a byte array. " +
                    "Optionally records the download action in user history."
    )
    @GetMapping("/{id}/file")
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
    @GetMapping("/{id}/thumbnail")
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
    @PutMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentInformation> updateDocumentWithFile(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) Set<String> courseCodes,
            @RequestParam(required = false) Set<String> majors,
            @RequestParam String level,
            @RequestParam Set<String> categories,
            @RequestParam(required = false) Set<String> tags,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        DocumentUpdateRequest updateRequest = new DocumentUpdateRequest(
                summary, courseCodes, majors, level, categories, tags
        );
        return ResponseEntity.ok(documentService.updateDocumentWithFile(id, file, updateRequest, jwt.getSubject()));
    }

    @Operation(summary = "Update document metadata",
            description = "Update document metadata like summary, course code, major, etc.")
    @PutMapping("/{id}")
    public ResponseEntity<DocumentInformation> updateDocument(
            @PathVariable String id,
            @RequestBody DocumentUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentService.updateDocument(id, request, jwt.getSubject()));
    }

    @Operation(summary = "Delete document",
            description = "Soft delete a document and its associated data")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        documentService.deleteDocument(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get tag suggestions",
            description = "Retrieve popular tags, optionally filtered by prefix")
    @GetMapping("/tags/suggestions")
    public ResponseEntity<Set<String>> getTagSuggestions(
            @RequestParam(required = false) String prefix) {
        return ResponseEntity.ok(documentService.getPopularTags(prefix));
    }

    @Operation(summary = "Get document share settings",
            description = "Retrieve document sharing configuration")
    @GetMapping("/{id}/sharing")
    public ResponseEntity<ShareSettings> getDocumentShareSettings(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentShareService.getDocumentShareSettings(id, jwt.getSubject()));
    }

    @Operation(summary = "Update share settings",
            description = "Update document sharing configuration including public access and specific users")
    @PutMapping("/{id}/sharing")
    public ResponseEntity<DocumentInformation> updateDocumentShareSettings(
            @PathVariable String id,
            @RequestBody UpdateShareSettingsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentShareService.updateDocumentShareSettings(id, request, jwt.getSubject()));
    }

    @Operation(summary = "Search users for sharing",
            description = "Search users that document can be shared with")
    @GetMapping("/sharing/users")
    public ResponseEntity<List<UserResponse>> searchShareableUsers(
            @RequestParam String query, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentShareService.searchShareableUsers(query, jwt.getSubject()));
    }

    @Operation(summary = "Get shared users details",
            description = "Get detailed information about users document is shared with")
    @PostMapping("/sharing/users/details")
    public ResponseEntity<List<UserResponse>> getShareableUserDetails(
            @RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(documentShareService.getShareableUserDetails(userIds));
    }

    @Operation(summary = "Get document statistics",
            description = "Retrieve document statistics including counts for views, downloads, updates, etc.")
    @GetMapping("/{id}/statistics")
    public ResponseEntity<DocumentStatisticsResponse> getDocumentStatistics(@PathVariable String id) {
        return ResponseEntity.ok(documentHistoryService.getDocumentStatistics(id));
    }

    @Operation(
            summary = "Get user's document history",
            description = "Retrieve paginated list of user's document interaction history with filtering options. " +
                    "Results are sorted by most recent first. Supports filtering by action type, date range, and document name."
    )
    @GetMapping
    public ResponseEntity<Page<UserHistoryResponse>> getUserHistory(
            @RequestParam(required = false) UserDocumentActionType actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Instant toDate,
            @RequestParam(required = false) String documentName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                documentHistoryService.getUserHistory(
                        jwt.getSubject(),
                        actionType,
                        fromDate,
                        toDate,
                        documentName,
                        pageable
                )
        );
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