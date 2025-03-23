package com.dms.document.search.controller;

import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.DocumentSearchRequest;
import com.dms.document.search.dto.SuggestionRequest;
import com.dms.document.search.service.DocumentSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/search")
public class DiscoverDocumentController {
    private final DocumentSearchService discoverDocumentSearchService;

    @PostMapping
    public ResponseEntity<Page<DocumentResponseDto>> searchDocuments(
            @RequestBody DocumentSearchRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();

        Page<DocumentResponseDto> results = discoverDocumentSearchService.searchDocuments(
                request,
                username
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

}