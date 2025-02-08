package com.dms.document.search.controller;

import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.DocumentSearchCriteria;
import com.dms.document.search.dto.DocumentSearchRequest;
import com.dms.document.search.model.DocumentInformation;
import com.dms.document.search.service.DocumentRecommendationService;
import com.dms.document.search.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService documentService;
    private final DocumentRecommendationService documentRecommendationService;

    @PostMapping("/me/search")
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

    @GetMapping("recommendation")
    public ResponseEntity<Page<DocumentResponseDto>> getRecommendationDocuments(
            @RequestParam(required = false) String documentId,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam int page,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getSubject();
        PageRequest pageable = PageRequest.of(page, size);

        // Get recommendations
        Page<DocumentResponseDto> recommendations = documentRecommendationService.getRecommendations(
                documentId,
                username,
                pageable
        );

        return ResponseEntity.ok(recommendations);
    }
}