package com.dms.document.interaction.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
@Builder
public class ThumbnailResponse {
    private byte[] data;
    private HttpStatus status;
    private boolean isPlaceholder;
    private Integer retryAfterSeconds;
}