package com.sdms.authentication.dtos.response;

import lombok.Data;

@Data
public class OperationError {
    private Long userId;
    private String error;
}