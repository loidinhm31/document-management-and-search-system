package com.sdms.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class InteractionResponse {
    private UUID id;
    private String type;
    private String details;
    private String targetId;
    private String username;
    private LocalDateTime createdAt;
}
