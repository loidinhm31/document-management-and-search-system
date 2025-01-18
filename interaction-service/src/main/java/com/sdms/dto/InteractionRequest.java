package com.sdms.dto;

import lombok.Data;

@Data
public class InteractionRequest {
    private String type;
    private String details;
    private String targetId;
}