package com.dms.document.interaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DocumentVersionResponse {
    private List<DocumentVersionDetail> versions;
    private Integer currentVersion;
    private Integer totalVersions;
}