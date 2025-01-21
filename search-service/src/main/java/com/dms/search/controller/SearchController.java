package com.dms.search.controller;

import com.dms.search.dto.DocumentResponseDto;
import com.dms.search.elasticsearch.DocumentIndex;
import com.dms.search.service.DocumentSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final DocumentSearchService documentSearchService;

    @GetMapping
    public ResponseEntity<Page<DocumentResponseDto>> searchDocuments(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        Pageable pageable = PageRequest.of(page, size);

        Page<DocumentResponseDto> results = documentSearchService.searchDocuments(
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