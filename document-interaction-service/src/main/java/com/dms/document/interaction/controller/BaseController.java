package com.dms.document.interaction.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public abstract class BaseController {
    protected static final String DOCUMENT_BASE_PATH = "/documents";
    protected static final String DOCUMENT_ID_PATH = "/{id}";

    protected <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok(body);
    }

    protected <T> ResponseEntity<T> ok() {
        return ResponseEntity.ok().build();
    }

    protected ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }
}