package com.dms.document.controller;

import com.dms.document.dto.DocumentUpdateRequest;
import com.dms.document.model.DocumentInformation;
import com.dms.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<DocumentInformation> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam String courseCode,
            @RequestParam String major,
            @RequestParam String level,
            @RequestParam String category,
            @RequestParam(required = false) Set<String> tags,
            @AuthenticationPrincipal Jwt jwt) throws IOException {

        return ResponseEntity.ok(documentService.uploadDocument(
                file, courseCode, major, level, category, tags, jwt.getSubject()));
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
        return ResponseEntity.ok(documentService.updateDocument(
                id, documentUpdateRequest, jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        documentService.deleteDocument(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/file")
    public ResponseEntity<DocumentInformation> updateDocumentFile(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        String username = jwt.getSubject();
        return ResponseEntity.ok(documentService.updateDocumentFile(id, file, username));
    }

    @GetMapping("/tags/suggestions")
    public ResponseEntity<Set<String>> getTagSuggestions(
            @RequestParam(required = false) String prefix) {
        return ResponseEntity.ok(documentService.getPopularTags(prefix));
    }

}