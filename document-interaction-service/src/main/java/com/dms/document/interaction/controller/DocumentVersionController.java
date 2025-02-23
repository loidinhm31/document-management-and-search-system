package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping(ApiConstant.API_VERSION + ApiConstant.DOCUMENT_BASE_PATH + ApiConstant.DOCUMENT_ID_PATH + "/versions")
@RequiredArgsConstructor
@Tag(name = "Document Versions", description = "APIs for managing document versions")
public class DocumentVersionController {
    private final DocumentService documentService;

    @Operation(summary = "Download specific document version",
            description = "Download document file for a specific version number")
    @GetMapping("/{versionNumber}/file")
    public ResponseEntity<byte[]> downloadDocumentVersion(
            @PathVariable String id,
            @PathVariable Integer versionNumber,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) boolean history,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        byte[] content = documentService.getDocumentVersionContent(id, versionNumber, jwt.getSubject(), action, history);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document\"")
                .body(content);
    }

    @Operation(summary = "Revert to previous version",
            description = "Revert document to a specific previous version")
    @PutMapping("/{versionNumber}")
    public ResponseEntity<DocumentInformation> revertToVersion(
            @PathVariable String id,
            @PathVariable Integer versionNumber,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentService.revertToVersion(id, versionNumber, jwt.getSubject()));
    }
}
