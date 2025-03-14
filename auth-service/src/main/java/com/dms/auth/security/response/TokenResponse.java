package com.dms.auth.security.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private String username;
    private List<String> roles;

    @With
    private boolean enabled;

    @With
    private int otpCount;

    @With
    private boolean locked;

    @With
    private boolean verified;

    public TokenResponse(String accessToken, String refreshToken, String tokenType, String username, List<String> roles) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.username = username;
        this.roles = roles;
    }

    public TokenResponse(String accessToken, String refreshToken, String tokenType, String username, List<String> roles, boolean enabled) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.username = username;
        this.roles = roles;
        this.enabled = enabled;
    }


    public TokenResponse(int otpCount, boolean locked, boolean verified) {
        this.otpCount = otpCount;
        this.locked = locked;
        this.verified = verified;
    }
}