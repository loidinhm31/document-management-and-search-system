package com.dms.document.interaction.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CommentTreeDTO {
    private final CommentResponse comment;
    private final List<CommentResponse> replies = new ArrayList<>();

    public void addReply(CommentResponse reply) {
        replies.add(reply);
        // Update the parent comment's replies
        comment.setReplies(new ArrayList<>(replies));
    }
}