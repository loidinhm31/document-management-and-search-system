package com.dms.auth.service.impl;

import com.dms.auth.entity.AuthToken;
import com.dms.auth.entity.User;
import com.dms.auth.security.jwt.JwtUtils;
import com.dms.auth.security.response.TokenResponse;
import com.dms.auth.security.service.CustomUserDetails;
import com.dms.auth.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseService {
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TokenService tokenService;

    public TokenResponse createToken(User user, CustomUserDetails userDetails, HttpServletRequest request) {
        // Generate JWT token
        String jwt = jwtUtils.generateTokenFromUsername(userDetails);

        // Save access token
        tokenService.createAccessToken(user, jwt, request);

        // Create refresh token
        AuthToken refreshToken = tokenService.createRefreshToken(user, request);

        // Get user roles
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

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