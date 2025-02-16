package com.dms.document.interaction.dto;

public record CommentRequest(
        String content,
        Long parentId
) {}