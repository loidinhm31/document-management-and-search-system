package com.sdms.document.controller;

import com.sdms.document.elasticsearch.DocumentIndex;
import com.sdms.document.entity.Document;
import com.sdms.document.entity.DocumentMetadata;
import com.sdms.document.service.DocumentSearchService;
import com.sdms.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService documentService;
    private final DocumentSearchService documentSearchService;

    @PostMapping
    public ResponseEntity<Document> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        String username = jwt.getSubject();
        return ResponseEntity.ok(documentService.uploadDocument(file, username));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        String username = jwt.getSubject();
        byte[] content = documentService.getDocumentContent(id, username);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document\"")
                .body(content);
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

}