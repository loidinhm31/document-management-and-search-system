package com.dms.document.interaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CommentResponse {
    private Long id;
    private String content;
    private String username;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean edited;
    private List<CommentResponse> replies;

}