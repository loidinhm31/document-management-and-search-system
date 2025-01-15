package com.example.demo2.security.request;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}