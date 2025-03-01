package com.dms.document.interaction.model.projection;

import com.dms.document.interaction.enums.DocumentReportStatus;

public interface DocumentReportProjection {
    String getDocumentId();
    Boolean getProcessed();
    DocumentReportStatus getStatus();
    Integer getReportCount();
    java.util.UUID getUpdatedBy();
    java.time.Instant getUpdatedAt();
}