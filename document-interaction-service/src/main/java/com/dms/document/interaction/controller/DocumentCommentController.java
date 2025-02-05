package com.dms.document.interaction.controller;

import com.dms.document.interaction.dto.CommentRequest;
import com.dms.document.interaction.dto.CommentResponse;
import com.dms.document.interaction.service.DocumentCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents/{documentId}/comments")
@RequiredArgsConstructor
public class DocumentCommentController {
    private final DocumentCommentService commentService;

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable String documentId,
            @RequestBody CommentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        CommentResponse comment = commentService.createComment(documentId, request, jwt.getSubject());
        return ResponseEntity.ok(comment);
    }

    @GetMapping
    public ResponseEntity<Page<CommentResponse>> getDocumentComments(
            @PathVariable String documentId,
            Pageable pageable) {
        Page<CommentResponse> comments = commentService.getDocumentComments(documentId, pageable);
        return ResponseEntity.ok(comments);
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable String documentId,
            @PathVariable Long commentId,
            @RequestBody CommentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        CommentResponse comment = commentService.updateComment(commentId, request, jwt.getSubject());
        return ResponseEntity.ok(comment);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable String documentId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal Jwt jwt) {
        commentService.deleteComment(commentId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}