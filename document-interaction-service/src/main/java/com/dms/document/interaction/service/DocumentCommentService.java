package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.CommentRequest;
import com.dms.document.interaction.dto.CommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DocumentCommentService {
    /**
     * Retrieves all comments for a document with pagination
     *
     * @param documentId The document ID
     * @param pageable Pagination parameters
     * @param username The username of the requesting user
     * @return Page of comment responses
     */
    Page<CommentResponse> getDocumentComments(String documentId, Pageable pageable, String username);

    /**
     * Creates a new comment or reply on a document
     *
     * @param documentId The document ID
     * @param request The comment request containing content and optional parent ID
     * @param username The username of the commenter
     * @return The created comment response
     */
    CommentResponse createComment(String documentId, CommentRequest request, String username);

    /**
     * Updates an existing comment
     *
     * @param documentId The document ID
     * @param commentId The comment ID to update
     * @param request The updated comment content
     * @param username The username of the comment owner
     * @return The updated comment response
     */
    CommentResponse updateComment(String documentId, Long commentId, CommentRequest request, String username);

    /**
     * Deletes a comment and its replies
     *
     * @param documentId The document ID
     * @param commentId The comment ID to delete
     * @param username The username of the comment owner
     */
    void deleteComment(String documentId, Long commentId, String username);
}