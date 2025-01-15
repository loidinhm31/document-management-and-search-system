package com.sdms.authentication.security.request;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}