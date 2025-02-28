package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.dto.AdminCommentReportResponse;
import com.dms.document.interaction.dto.AdminDocumentReportResponse;
import com.dms.document.interaction.dto.CommentReportDetailResponse;
import com.dms.document.interaction.dto.DocumentReportDetail;
import com.dms.document.interaction.enums.CommentReportStatus;
import com.dms.document.interaction.enums.DocumentReportStatus;
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
            @RequestParam DocumentReportStatus status,
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
            @RequestParam(required = false) DocumentReportStatus status,
            @RequestParam(required = false) String reportTypeCode,
            Pageable pageable) {

        return ResponseEntity.ok(documentReportService.getAdminDocumentReports(
                documentTitle, fromDate, toDate, status, reportTypeCode, pageable));
    }

    @Operation(summary = "Get all report details for a specific document",
            description = "Admin endpoint to retrieve all detailed reports for a specific document")
    @GetMapping("/documents/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DocumentReportDetail>> getDocumentReportDetails(
            @PathVariable String documentId, @RequestParam Boolean processed) {
        return ResponseEntity.ok(documentReportService.getDocumentReportDetails(documentId, processed));
    }

    @Operation(summary = "Update a comment report status",
            description = "Admin endpoint to update a comment report status")
    @PutMapping("/comments/{commentId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminCommentReportResponse> resolveCommentReport(
            @PathVariable Long commentId,
            @RequestParam CommentReportStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        commentReportService.resolveCommentReport(
                commentId, status, jwt.getSubject());
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
            @RequestParam(required = false) CommentReportStatus status,
            Pageable pageable) {

        return ResponseEntity.ok(commentReportService.getAdminCommentReports(fromDate, toDate, commentContent, reportTypeCode, status, pageable));
    }

    @Operation(summary = "Get all reports for a specific comment",
            description = "Admin endpoint to retrieve all reports for a specific comment")
    @GetMapping("/comments/{commentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CommentReportDetailResponse>> getCommentReportsByCommentId(
            @PathVariable Long commentId) {
        return ResponseEntity.ok(commentReportService.getCommentReportsByCommentId(commentId));
    }
}
