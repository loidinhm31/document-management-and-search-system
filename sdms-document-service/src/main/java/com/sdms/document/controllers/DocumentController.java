package com.sdms.document.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok("Successfully accessed document service with token: " + jwt.getTokenValue());
    }

    @PostMapping("/test")
    public ResponseEntity<String> testPostEndpoint(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok("Successfully created document with token: " + jwt.getTokenValue());
    }
}