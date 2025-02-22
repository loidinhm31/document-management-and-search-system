package com.dms.document.interaction.dto;

import com.dms.document.interaction.enums.MasterDataType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
public class MasterDataResponse {
    private String id;
    private MasterDataType type;
    private String code;
    private TranslationDTO translations;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean isActive;
    private String parentId;
}