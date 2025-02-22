package com.dms.document.interaction.controller;


import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentVersion;
import com.dms.document.interaction.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService documentService;

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

        return ResponseEntity.ok(documentService.uploadDocument(
                file, summary, courseCode, major, level, category, tags, jwt.getSubject()));
    }

    @GetMapping("/{id}/downloads")
    public ResponseEntity<byte[]> getDocument(
            @PathVariable String id,
            @RequestParam(required = false) String action,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        String username = jwt.getSubject();
        byte[] content = documentService.getDocumentContent(id, username, action);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document\"")
                .body(content);
    }

    @GetMapping("/{id}/thumbnails")
    public ResponseEntity<byte[]> getDocumentThumbnail(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) throws IOException {

        String username = jwt.getSubject();
        DocumentInformation document = documentService.getDocumentDetails(id, username);
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

    @GetMapping("/{id}")
    public ResponseEntity<DocumentInformation> getDocumentDetails(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        DocumentInformation document = documentService.getDocumentDetails(id, username);
        return ResponseEntity.ok(document);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentInformation> updateDocument(
            @PathVariable String id,
            @RequestBody DocumentUpdateRequest documentUpdateRequest,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentService.updateDocument(id, documentUpdateRequest, jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        documentService.deleteDocument(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/file")
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
        DocumentUpdateRequest metadata = new DocumentUpdateRequest(
                summary, courseCode, major, level, category, tags
        );
        DocumentInformation document = documentService.updateDocumentWithFile(
                id,
                file,
                metadata,
                jwt.getSubject()
        );

        return ResponseEntity.ok(document);
    }

    @GetMapping("/tags/suggestions")
    public ResponseEntity<Set<String>> getTagSuggestions(
            @RequestParam(required = false) String prefix) {
        return ResponseEntity.ok(documentService.getPopularTags(prefix));
    }

    @GetMapping("/{documentId}/versions/{versionNumber}/download")
    public ResponseEntity<byte[]> downloadDocumentVersion(
            @PathVariable String documentId,
            @PathVariable Integer versionNumber,
            @RequestParam(required = false) String action,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        String username = jwt.getSubject();
        byte[] content = documentService.getDocumentVersionContent(documentId, versionNumber, username, action);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"document\"")
                .body(content);
    }

    @PostMapping("/{documentId}/versions/{versionNumber}/revert")
    public ResponseEntity<DocumentInformation> revertToVersion(
            @PathVariable String documentId,
            @PathVariable Integer versionNumber,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        DocumentInformation document = documentService.revertToVersion(documentId, versionNumber, username);
        return ResponseEntity.ok(document);
    }

}