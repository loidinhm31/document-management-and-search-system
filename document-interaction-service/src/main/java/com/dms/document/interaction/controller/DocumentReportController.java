package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.dto.ReportRequest;
import com.dms.document.interaction.dto.ReportResponse;
import com.dms.document.interaction.enums.ReportStatus;
import com.dms.document.interaction.service.DocumentReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiConstant.API_VERSION + ApiConstant.DOCUMENT_BASE_PATH + ApiConstant.DOCUMENT_ID_PATH + "/reports")
@RequiredArgsConstructor
@Tag(name = "Document Reports", description = "APIs for managing document violation reports")
public class DocumentReportController {
    private final DocumentReportService documentReportService;

    @Operation(summary = "Report a document violation",
            description = "Submit a report for document violation")
    @PostMapping
    public ResponseEntity<ReportResponse> reportDocument(
            @PathVariable String id,
            @RequestBody ReportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentReportService.createReport(id, request, jwt.getSubject()));
    }

    @Operation(summary = "Get user's report for document",
            description = "Get the current user's report for a specific document if exists")
    @GetMapping("/user")
    public ResponseEntity<ReportResponse> getUserReport(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return documentReportService.getUserReport(id, jwt.getSubject())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update report status",
            description = "Update status of document violation report (Admin only)")
    @PutMapping("/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> updateReportStatus(
            @PathVariable String id,
            @RequestParam ReportStatus status,
            @RequestParam(required = false) String resolutionNote,
            @AuthenticationPrincipal Jwt jwt) {
        documentReportService.updateReportStatus(id, status, jwt.getSubject());
        return ResponseEntity.ok().build();
    }


}