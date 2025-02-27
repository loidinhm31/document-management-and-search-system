package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.dto.AdminCommentReportResponse;
import com.dms.document.interaction.dto.AdminDocumentReportResponse;
import com.dms.document.interaction.dto.DocumentReportDetail;
import com.dms.document.interaction.enums.ReportStatus;
import com.dms.document.interaction.service.CommentReportService;
import com.dms.document.interaction.service.DocumentReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(ApiConstant.API_VERSION + "/reports")
@RequiredArgsConstructor
@Tag(name = "Admin Reports", description = "Admin APIs for managing violation reports")
public class ReportController {
    private final DocumentReportService documentReportService;
    private final CommentReportService commentReportService;

    @Operation(summary = "Update report status",
            description = "Update status of document violation report (Admin only)")
    @PutMapping("/documents/{documentId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateReportStatus(
            @PathVariable String documentId,
            @RequestParam ReportStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        documentReportService.updateReportStatus(documentId, status, jwt.getSubject());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get all document reports with filters",
            description = "Admin endpoint to retrieve paginated list of document reports with search and filter options")
    @GetMapping("/documents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdminDocumentReportResponse>> getAllDocumentReports(
            @RequestParam(required = false) String documentTitle,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Instant toDate,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) String reportTypeCode,
            Pageable pageable) {

        return ResponseEntity.ok(documentReportService.getAdminDocumentReports(
                documentTitle, fromDate, toDate, status, reportTypeCode, pageable));
    }

    @Operation(summary = "Get report details for a specific document",
            description = "Admin endpoint to retrieve detailed reports for a specific document")
    @GetMapping("/documents/{documentId}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DocumentReportDetail>> getDocumentReportDetails(
            @PathVariable String documentId) {
        return ResponseEntity.ok(documentReportService.getDocumentReportDetails(documentId));
    }

    @Operation(summary = "Resolve a comment report",
            description = "Admin endpoint to mark a comment report as resolved or unresolved")
    @PutMapping("/comments/{reportId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminCommentReportResponse> resolveCommentReport(
            @PathVariable Long reportId,
            @RequestParam boolean resolved,
            @AuthenticationPrincipal Jwt jwt) {
        commentReportService.resolveCommentReport(
                reportId, resolved, jwt.getSubject());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get all comment reports with filters",
            description = "Admin endpoint to retrieve paginated list of comment reports with search and filter options")
    @GetMapping("/comments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdminCommentReportResponse>> getAllCommentReports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Instant toDate,
            @RequestParam(required = false) String commentContent,
            @RequestParam(required = false) String reportTypeCode,
            @RequestParam(required = false) Boolean resolved,
            Pageable pageable) {

        return ResponseEntity.ok(commentReportService.getAdminCommentReports(fromDate, toDate, commentContent, reportTypeCode, resolved, pageable));
    }
}
