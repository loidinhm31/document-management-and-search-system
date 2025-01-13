package com.example.demo2.dtos.response;

import lombok.Data;

@Data
public class OperationError {
    private Long userId;
    private String error;
}