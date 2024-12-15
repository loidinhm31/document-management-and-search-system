package com.example.demo2.dto;

import lombok.Data;

public class AuthDTO {

    @Data
    public static class RegisterRequest {
        private String email;
        private String password;
        private String name;
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
        private String email;
        private String name;
    }

    @Data
    public static class TokenRefreshRequest {
        private String refreshToken;
    }

    @Data
    public static class TokenRefreshResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
    }
}