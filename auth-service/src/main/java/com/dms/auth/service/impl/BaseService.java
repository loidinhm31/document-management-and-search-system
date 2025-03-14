package com.dms.auth.service.impl;

import com.dms.auth.entity.RefreshToken;
import com.dms.auth.entity.User;
import com.dms.auth.security.jwt.JwtUtils;
import com.dms.auth.security.response.TokenResponse;
import com.dms.auth.security.service.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseService {
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    public TokenResponse createToken(User user, CustomUserDetails userDetails, HttpServletRequest request) {
        // Create refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, request);

        // Get user roles
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Generate JWT token
        String jwt = jwtUtils.generateTokenFromUsername(userDetails);

        return new TokenResponse(
                jwt,
                refreshToken.getToken(),
                "Bearer",
                userDetails.getUsername(),
                roles,
                user.isEnabled()
        );
    }
}