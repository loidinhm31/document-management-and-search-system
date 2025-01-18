package com.sdms.document.controller;

import com.sdms.document.elasticsearch.DocumentIndex;
import com.sdms.document.enums.CourseLevel;
import com.sdms.document.enums.DocumentCategory;
import com.sdms.document.enums.Major;
import com.sdms.document.model.DocumentInformation;
import com.sdms.document.service.DocumentSearchService;
import com.sdms.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService documentService;
    private final DocumentSearchService documentSearchService;

    @PostMapping
    public ResponseEntity<DocumentInformation> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam String courseCode,
            @RequestParam Major major,
            @RequestParam CourseLevel level,
            @RequestParam DocumentCategory category,
            @RequestParam(required = false) Set<String> tags,
            @AuthenticationPrincipal Jwt jwt) throws IOException {

        return ResponseEntity.ok(documentService.uploadDocument(
                file, courseCode, major, level, category, tags, jwt.getSubject()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        String username = jwt.getSubject();
        byte[] content = documentService.getDocumentContent(id, username);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document\"")
                .body(content);
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<DocumentInformation> updateDocumentTags(
            @PathVariable String id,
            @RequestBody Set<String> tags,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentService.updateTags(id, tags, jwt.getSubject()));
    }

    @GetMapping("/tags/suggestions")
    public ResponseEntity<Set<String>> getTagSuggestions(
            @RequestParam(required = false) String prefix) {
        return ResponseEntity.ok(documentService.getPopularTags(prefix));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<DocumentIndex>> searchDocuments(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        Pageable pageable = PageRequest.of(page, size);

        Page<DocumentIndex> results = documentSearchService.searchDocuments(
                query,
                username,
                pageable
        );

        return ResponseEntity.ok(results);
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> getSuggestions(
            @RequestParam String query,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();

        List<String> suggestions = documentSearchService.getSuggestions(query, username);
        return ResponseEntity.ok(suggestions);
    }

}