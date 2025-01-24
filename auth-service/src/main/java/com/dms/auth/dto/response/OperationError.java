package com.dms.auth.dto.response;

import lombok.Data;

@Data
public class OperationError {
    private Long userId;
    private String error;
}