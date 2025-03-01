package com.dms.document.interaction.model.projection;

import com.dms.document.interaction.enums.CommentReportStatus;

public interface CommentReportProjection {
    Long getCommentId();
    String getDocumentId();
    String getCommentContent();
    Boolean getProcessed();
    CommentReportStatus getStatus();
    Integer getReportCount();
    java.util.UUID getReporterId();
    java.util.UUID getCommentUserId();
    java.util.UUID getUpdatedBy();
    String getReportTypeCode();
    String getDescription();
    java.time.Instant getCreatedAt();
    java.time.Instant getUpdatedAt();
}