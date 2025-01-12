package com.example.demo2.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorResponse {
    private int status;
    private String message;
    private String details;

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
    }
}