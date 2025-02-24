package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.dto.AdminCommentReportResponse;
import com.dms.document.interaction.dto.CommentReportSearchRequest;
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
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> updateReportStatus(
            @PathVariable String documentId,
            @RequestParam ReportStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        documentReportService.updateReportStatus(documentId, status, jwt.getSubject());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Resolve a comment report",
            description = "Admin endpoint to mark a comment report as resolved or unresolved")
    @PutMapping("/comments/{reportId}/resolve")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<AdminCommentReportResponse> resolveCommentReport(
            @PathVariable Long reportId,
            @RequestParam boolean resolved,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(commentReportService.resolveCommentReport(
                reportId, resolved, jwt.getSubject()));
    }

    @Operation(summary = "Get all comment reports with filters",
            description = "Admin endpoint to retrieve paginated list of comment reports with search and filter options")
    @GetMapping("/comments")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Page<AdminCommentReportResponse>> getAllCommentReports(
            @RequestParam(required = false) String commentContent,
            @RequestParam(required = false) String reportTypeCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(required = false) Boolean resolved,
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {

        CommentReportSearchRequest searchRequest = new CommentReportSearchRequest(
                commentContent, reportTypeCode, createdFrom, createdTo, resolved
        );

        return ResponseEntity.ok(commentReportService.getAdminCommentReports(searchRequest, pageable));
    }
}
