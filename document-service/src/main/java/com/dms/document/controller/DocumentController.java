package com.dms.document.controller;

import com.dms.document.dto.*;
import com.dms.document.enums.DocumentStatus;
import com.dms.document.model.DocumentInformation;
import com.dms.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
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

    @PostMapping("/user/search")
    public ResponseEntity<Page<DocumentInformation>> searchUserDocuments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) DocumentSearchRequest request) {
        String username = jwt.getSubject();

        // If no request body provided, create default request
        if (request == null) {
            request = DocumentSearchRequest.builder()
                    .page(0)
                    .size(12)
                    .sortField("createdAt")
                    .sortDirection("desc")
                    .build();
        }

        // Create search criteria from request
        DocumentSearchCriteria criteria = DocumentSearchCriteria.builder()
                .search(request.getSearch())
                .major(request.getMajor())
                .level(request.getLevel())
                .category(request.getCategory())
                .tags(request.getTags())
                .sortField(request.getSortField())
                .sortDirection(request.getSortDirection())
                .build();

        Page<DocumentInformation> documents = documentService.getUserDocuments(
                username,
                criteria,
                request.getPage(),
                request.getSize() > 0 ? request.getSize() : 12
        );

        return ResponseEntity.ok(documents);
    }

    @GetMapping("/downloads/{id}")
    public ResponseEntity<byte[]> getDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        String username = jwt.getSubject();
        byte[] content = documentService.getDocumentContent(id, username);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document\"")
                .body(content);
    }

    @GetMapping("/thumbnails/{id}")
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
            // Generate ETag considering both document update time and file content
            String eTag = generateETag(document);

            // Check if client's cached version is still valid
            if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(eTag)
                        .build();
            }

            headers.setETag(eTag);
            headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS)
                    .mustRevalidate()
                    .noTransform());
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
        // Include all relevant information that should trigger a cache invalidation
        String contentKey = String.format("%s_%s_%s_%s_%s",
                document.getId(),
                document.getUpdatedAt().getTime(),
                document.getFileSize(),
                document.getFilename(),
                // Include thumbnail path or "none" if not yet generated
                Optional.ofNullable(document.getThumbnailPath()).orElse("none")
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
            @RequestParam String courseCode,
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

    @GetMapping("/{id}/share")
    public ResponseEntity<ShareSettings> getShareSettings(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        ShareSettings settings = documentService.getShareSettings(id, username);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/{id}/share")
    public ResponseEntity<DocumentInformation> updateShareSettings(
            @PathVariable String id,
            @RequestBody UpdateShareSettingsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        DocumentInformation document = documentService.updateShareSettings(id, request, username);
        return ResponseEntity.ok(document);
    }

    @GetMapping("/tags/suggestions")
    public ResponseEntity<Set<String>> getTagSuggestions(
            @RequestParam(required = false) String prefix) {
        return ResponseEntity.ok(documentService.getPopularTags(prefix));
    }

}