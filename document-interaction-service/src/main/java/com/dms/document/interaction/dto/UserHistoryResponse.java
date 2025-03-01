package com.dms.document.interaction.dto;

import com.dms.document.interaction.enums.UserDocumentActionType;

import java.time.Instant;

public record UserHistoryResponse(
        String id,
        UserDocumentActionType actionType,
        String documentId,
        String documentTitle,
        String detail,
        Integer version,
        Instant timestamp
) {}