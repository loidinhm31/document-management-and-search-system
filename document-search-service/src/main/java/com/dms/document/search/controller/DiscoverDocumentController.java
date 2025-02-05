package com.dms.document.search.controller;

import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.DocumentSearchRequest;
import com.dms.document.search.dto.SuggestionRequest;
import com.dms.document.search.service.DiscoverDocumentSearchService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/search")
public class DiscoverDocumentController {
    private final DiscoverDocumentSearchService discoverDocumentSearchService;

    @PostMapping
    public ResponseEntity<Page<DocumentResponseDto>> searchDocuments(
            @RequestBody DocumentSearchRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();

        // Get sort direction, default to DESC if not specified
        Sort.Direction sortDirection = Sort.Direction.DESC;
        if (StringUtils.isNotBlank(request.getSortDirection())) {
            sortDirection = Sort.Direction.fromString(request.getSortDirection().toUpperCase());
        }

        // Get sort field, default to createdAt if not specified
        String sortField = "created_at";
        if (StringUtils.isNotBlank(request.getSortField())) {
            sortField = getSortableFieldName(request.getSortField());
        }

        // Create pageable with sort
        PageRequest pageable = PageRequest.of(
                request.getPage(),
                request.getSize() > 0 ? request.getSize() : 10,
                Sort.by(sortDirection, sortField)
        );

        Page<DocumentResponseDto> results = discoverDocumentSearchService.searchDocuments(
                request,
                username,
                pageable
        );

        return ResponseEntity.ok(results);
    }

    @PostMapping("/suggestions")
    public ResponseEntity<List<String>> getSuggestions(
            @RequestBody SuggestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();

        List<String> suggestions = discoverDocumentSearchService.getSuggestions(
                request,
                username
        );
        return ResponseEntity.ok(suggestions);
    }

    private String getSortableFieldName(String field) {
        return switch (field) {
            case "filename" -> "filename.raw";
            case "content" -> "content.keyword";
            case "created_at", "createdAt" -> "created_at";
            default -> field;
        };
    }
}