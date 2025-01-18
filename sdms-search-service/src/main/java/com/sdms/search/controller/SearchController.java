package com.sdms.search.controller;

import com.sdms.search.elasticsearch.DocumentIndex;
import com.sdms.search.enums.CourseLevel;
import com.sdms.search.enums.DocumentCategory;
import com.sdms.search.enums.Major;
import com.sdms.search.model.DocumentInformation;
import com.sdms.search.service.DocumentService;
import com.sdms.search.service.DocumentSearchService;
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
@RequestMapping("/api/v1/search")
public class SearchController {
    private final DocumentSearchService documentSearchService;

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