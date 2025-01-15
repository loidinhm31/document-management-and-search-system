package com.sdms.auth.dtos.response;

import lombok.Data;

@Data
public class OperationError {
    private Long userId;
    private String error;
}