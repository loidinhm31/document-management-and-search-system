package com.dms.document.search.controller;

import com.dms.document.search.dto.DocumentSearchCriteria;
import com.dms.document.search.dto.DocumentSearchRequest;
import com.dms.document.search.model.DocumentInformation;
import com.dms.document.search.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService documentService;

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

}