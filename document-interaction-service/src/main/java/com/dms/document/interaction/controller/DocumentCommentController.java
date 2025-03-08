package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.dto.CommentReportRequest;
import com.dms.document.interaction.dto.CommentReportResponse;
import com.dms.document.interaction.dto.CommentRequest;
import com.dms.document.interaction.dto.CommentResponse;
import com.dms.document.interaction.service.CommentReportService;
import com.dms.document.interaction.service.DocumentCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstant.API_VERSION + ApiConstant.DOCUMENT_BASE_PATH + ApiConstant.DOCUMENT_ID_PATH + "/comments")
@RequiredArgsConstructor
@Tag(name = "Document Comments", description = "APIs for managing document comments")
public class DocumentCommentController {
    private final DocumentCommentService commentService;
    private final CommentReportService commentReportService;

    @Operation(summary = "Create a new comment",
            description = "Add a new comment to a document")
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable String id,
            @RequestBody CommentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(commentService.createComment(id, request, jwt.getSubject()));
    }

    @Operation(summary = "Get document comments",
            description = "Retrieve paginated list of comments for a document")
    @GetMapping
    public ResponseEntity<Page<CommentResponse>> getDocumentComments(
            @PathVariable String id,
            Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(commentService.getDocumentComments(id, pageable, jwt.getSubject()));
    }

    @Operation(summary = "Update comment",
            description = "Update content of an existing comment")
    @PutMapping("/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable String id,
            @PathVariable Long commentId,
            @RequestBody CommentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(commentService.updateComment(id, commentId, request, jwt.getSubject()));
    }

    @Operation(summary = "Delete comment",
            description = "Delete a comment and its replies")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable String id,
            @PathVariable Long commentId,
            @AuthenticationPrincipal Jwt jwt) {
        commentService.deleteComment(id, commentId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Report a comment violation",
            description = "Submit a report for comment violation")
    @PostMapping("{commentId}")
    public ResponseEntity<CommentReportResponse> reportComment(
            @PathVariable String id,
            @PathVariable Long commentId,
            @RequestBody CommentReportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(commentReportService.createReport(id, commentId, request, jwt.getSubject()));
    }

    @Operation(summary = "Get user's report for comment",
            description = "Get the current user's report for a specific comment if exists")
    @GetMapping("{commentId}/user")
    public ResponseEntity<CommentReportResponse> getUserReport(
            @PathVariable String id,
            @PathVariable Long commentId,
            @AuthenticationPrincipal Jwt jwt) {
        return commentReportService.getUserReport(id, commentId, jwt.getSubject())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}