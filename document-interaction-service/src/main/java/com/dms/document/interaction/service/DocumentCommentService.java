package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.CommentRequest;
import com.dms.document.interaction.dto.CommentResponse;
import com.dms.document.interaction.dto.UserDto;
import com.dms.document.interaction.model.DocumentComment;
import com.dms.document.interaction.repository.DocumentCommentRepository;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentCommentService {
    private final DocumentCommentRepository commentRepository;
    private final UserClient userClient;

    @Transactional(readOnly = true)
    public Page<CommentResponse> getDocumentComments(String documentId, Pageable pageable) {
        // Get all comments with their replies using recursive query
        List<DocumentComment> allComments = commentRepository.findCommentsWithReplies(
                documentId,
                pageable.getPageSize(),
                (int) pageable.getOffset()
        );

        // Count total top-level comments for pagination
        long totalComments = commentRepository.countTopLevelComments(documentId);

        // Get all unique user IDs from all comments
        Set<UUID> userIds = allComments.stream()
                .map(DocumentComment::getUserId)
                .collect(Collectors.toSet());

        // Batch fetch user data
        Map<UUID, UserDto> userMap = batchFetchUsers(userIds);

        // Build comment tree structure
        List<CommentResponse> commentTree = buildCommentTree(allComments, userMap);

        return new PageImpl<>(commentTree, pageable, totalComments);
    }

    @Transactional
    public CommentResponse createComment(String documentId, CommentRequest request, String username) {
        UserDto userDto = getUserByUsername(username);

        DocumentComment comment = new DocumentComment();
        comment.setDocumentId(documentId);
        comment.setUserId(userDto.getUserId());
        comment.setContent(request.content());
        comment.setParentId(request.parentId());

        DocumentComment savedComment = commentRepository.save(comment);

        // Initialize empty replies list for new comment
        savedComment.setReplies(new ArrayList<>());

        return mapToCommentResponse(savedComment, Collections.singletonMap(userDto.getUserId(), userDto));
    }

    @Transactional
    public CommentResponse updateComment(Long commentId, CommentRequest request, String username) {
        UserDto userDto = getUserByUsername(username);

        DocumentComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        if (!comment.getUserId().equals(userDto.getUserId())) {
            throw new IllegalStateException("Not authorized to edit this comment");
        }

        comment.setContent(request.content());
        comment.setEdited(true);
        comment.setUpdatedAt(LocalDateTime.now());

        DocumentComment updatedComment = commentRepository.save(comment);
        return mapToCommentResponse(updatedComment, Collections.singletonMap(userDto.getUserId(), userDto));
    }

    @Transactional
    public void deleteComment(Long commentId, String username) {
        UserDto userDto = getUserByUsername(username);

        DocumentComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        if (!comment.getUserId().equals(userDto.getUserId())) {
            throw new IllegalStateException("Not authorized to delete this comment");
        }

        comment.setDeleted(true);
        comment.setContent("[deleted]");
        commentRepository.save(comment);
    }

    private Map<UUID, UserDto> batchFetchUsers(Set<UUID> userIds) {
        try {
            ResponseEntity<List<UserDto>> response = userClient.getUsersByIds(new ArrayList<>(userIds));
            if (response.getBody() != null) {
                return response.getBody().stream()
                        .collect(Collectors.toMap(
                                UserDto::getUserId,
                                Function.identity(),
                                (existing, replacement) -> existing
                        ));
            }
        } catch (Exception e) {
            log.error("Error fetching user data", e);
        }
        return new HashMap<>();
    }

    private List<CommentResponse> buildCommentTree(List<DocumentComment> comments, Map<UUID, UserDto> userMap) {
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

    private UserDto getUserByUsername(String username) {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        return response.getBody();
    }

    private Map<UUID, UserDto> fetchUserMap(List<UUID> userIds) {
        ResponseEntity<List<UserDto>> response = userClient.getUsersByIds(userIds);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new InvalidDataAccessResourceUsageException("Failed to fetch user data");
        }
        return response.getBody().stream()
                .collect(Collectors.toMap(
                        UserDto::getUserId,
                        Function.identity()
                ));
    }

    private CommentResponse mapToCommentResponse(
            DocumentComment comment,
            Map<UUID, UserDto> userMap) {
        UserDto user = userMap.get(comment.getUserId());
        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .username(user != null ? user.getUsername() : "N/A")
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .edited(comment.isEdited())
                .build();
    }
}