package com.dms.document.interaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
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
    private Integer flag;
    private boolean reportedByUser;
    private List<CommentResponse> replies;

}