package com.dms.document.interaction.dto;

import com.dms.document.interaction.enums.MasterDataType;
import lombok.Data;

@Data
public class MasterDataRequest {
    private MasterDataType type;
    private String code;
    private TranslationDTO translations;
    private String description;
    private boolean isActive;
    private String parentId;
}