package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.CommentRequest;
import com.dms.document.interaction.dto.CommentResponse;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.InteractionType;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentComment;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.UserDocumentHistory;
import com.dms.document.interaction.repository.DocumentCommentRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.UserDocumentHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentCommentService {
    private final UserClient userClient;
    private final DocumentCommentRepository documentCommentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentNotificationService documentNotificationService;
    private final DocumentPreferencesService documentPreferencesService;
    private final UserDocumentHistoryRepository userDocumentHistoryRepository;

    @Transactional(readOnly = true)
    public Page<CommentResponse> getDocumentComments(String documentId, Pageable pageable, String username) {
        UserResponse userResponse = getUserByUsername(username);

        DocumentInformation documentInformation = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userResponse.userId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Get all comments with their replies using recursive query
        List<DocumentComment> allComments = documentCommentRepository.findCommentsWithReplies(
                documentInformation.getId(),
                pageable.getPageSize(),
                (int) pageable.getOffset()
        );

        // Count total top-level comments for pagination
        long totalComments = documentCommentRepository.countTopLevelComments(documentId);

        // Get all unique user IDs from all comments
        Set<UUID> userIds = allComments.stream()
                .map(DocumentComment::getUserId)
                .collect(Collectors.toSet());

        // Batch fetch user data
        Map<UUID, UserResponse> userMap = batchFetchUsers(userIds);

        // Build comment tree structure
        List<CommentResponse> commentTree = buildCommentTree(allComments, userMap);

        return new PageImpl<>(commentTree, pageable, totalComments);
    }

    @Transactional
    public CommentResponse createComment(String documentId, CommentRequest request, String username) {
        UserResponse userResponse = getUserByUsername(username);

        DocumentInformation documentInformation = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userResponse.userId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        DocumentComment comment = new DocumentComment();
        comment.setDocumentId(documentInformation.getId());
        comment.setUserId(userResponse.userId());
        comment.setContent(request.content());
        comment.setParentId(request.parentId());

        DocumentComment savedComment = documentCommentRepository.save(comment);

        // Initialize empty replies list for new comment
        savedComment.setReplies(new ArrayList<>());

        CompletableFuture.runAsync(() -> {
            // History
            userDocumentHistoryRepository.save(UserDocumentHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.COMMENT)
                    .version(documentInformation.getCurrentVersion())
                    .detail(comment.getContent())
                    .createdAt(Instant.now())
                    .build());

            // Only notify if this is a new commenter
            documentNotificationService.handleCommentNotification(
                    documentId,
                    username,
                    userResponse.userId(),
                    savedComment.getId()
            );

            documentPreferencesService.recordInteraction(userResponse.userId(), documentId, InteractionType.COMMENT);
        });
        return mapToCommentResponse(savedComment, Collections.singletonMap(userResponse.userId(), userResponse));
    }

    @Transactional
    public CommentResponse updateComment(Long commentId, CommentRequest request, String username) {
        UserResponse userResponse = getUserByUsername(username);

        DocumentComment comment = documentCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        if (!comment.getUserId().equals(userResponse.userId())) {
            throw new IllegalStateException("Not authorized to edit this comment");
        }

        comment.setContent(request.content());
        comment.setEdited(true);
        comment.setUpdatedAt(Instant.now());

        DocumentComment updatedComment = documentCommentRepository.save(comment);
        return mapToCommentResponse(updatedComment, Collections.singletonMap(userResponse.userId(), userResponse));
    }

    @Transactional
    public void deleteComment(Long commentId, String username) {
        UserResponse userResponse = getUserByUsername(username);

        DocumentComment comment = documentCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        if (!comment.getUserId().equals(userResponse.userId())) {
            throw new IllegalStateException("Not authorized to delete this comment");
        }

        // Fetch all descendant comments in a single query using recursive CTE
        List<Long> descendantIds = documentCommentRepository.findAllDescendantIds(commentId);

        // Bulk update all affected comments in a single transaction
        documentCommentRepository.markCommentsAsDeleted(descendantIds);
    }

    private Map<UUID, UserResponse> batchFetchUsers(Set<UUID> userIds) {
        try {
            ResponseEntity<List<UserResponse>> response = userClient.getUsersByIds(new ArrayList<>(userIds));
            if (response.getBody() != null) {
                return response.getBody().stream()
                        .collect(Collectors.toMap(
                                UserResponse::userId,
                                Function.identity(),
                                (existing, replacement) -> existing
                        ));
            }
        } catch (Exception e) {
            log.error("Error fetching user data", e);
        }
        return new HashMap<>();
    }

    private List<CommentResponse> buildCommentTree(List<DocumentComment> comments, Map<UUID, UserResponse> userMap) {
        // Use LinkedHashMap to maintain order
        Map<Long, CommentResponse> responseMap = new LinkedHashMap<>();
        List<CommentResponse> rootComments = new ArrayList<>();

        // Single pass comment tree building
        for (DocumentComment comment : comments) {
            CommentResponse response = mapToCommentResponse(comment, userMap);
            responseMap.put(comment.getId(), response);

            if (comment.getParentId() == null) {
                rootComments.add(response);
            } else {
                CommentResponse parentResponse = responseMap.get(comment.getParentId());
                if (parentResponse != null) {
                    if (parentResponse.getReplies() == null) {
                        parentResponse.setReplies(new ArrayList<>());
                    }
                    parentResponse.getReplies().add(response);
                }
            }
        }

        return rootComments;
    }

    private UserResponse getUserByUsername(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        return response.getBody();
    }

    private CommentResponse mapToCommentResponse(
            DocumentComment comment,
            Map<UUID, UserResponse> userMap) {
        UserResponse user = userMap.get(comment.getUserId());
        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .username(user != null ? user.username() : "N/A")
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .edited(comment.isEdited())
                .build();
    }
}