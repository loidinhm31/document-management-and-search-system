package com.example.demo2.service;


import com.example.demo2.dto.AuthDTO.*;
import com.example.demo2.entity.RefreshToken;
import com.example.demo2.entity.User;
import com.example.demo2.enums.AuthProvider;
import com.example.demo2.repository.RefreshTokenRepository;
import com.example.demo2.repository.UserRepository;
import com.example.demo2.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already taken");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setProvider(AuthProvider.LOCAL);

        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        user.setRoles(roles);

        User savedUser = userRepository.save(user);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        return createAuthResponse(authentication);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        return createAuthResponse(authentication);
    }

    @Transactional
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        User user = refreshToken.getUser();
        String newAccessToken = jwtUtil.generateToken(
                org.springframework.security.core.userdetails.User.builder()
                        .username(user.getEmail())
                        .password("")
                        .authorities(user.getRoles().stream()
                                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                                .toList())
                        .build()
        );

        // Generate new refresh token
        String newRefreshToken = generateRefreshToken(user);

        TokenRefreshResponse response = new TokenRefreshResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        return response;
    }

    private AuthResponse createAuthResponse(Authentication authentication) {
        org.springframework.security.core.userdetails.User userDetails =
                (org.springframework.security.core.userdetails.User) authentication.getPrincipal();

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String accessToken = jwtUtil.generateToken(userDetails);
        String refreshToken = generateRefreshToken(user);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        return response;
    }

    @Transactional
    public String generateRefreshToken(User user) {
        // Remove existing refresh token if any
        refreshTokenRepository.deleteByUser(user);

        // Create new refresh token
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(System.currentTimeMillis() + (7L * 24L * 60L * 60L * 1000L)); // 7 days
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getToken();
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}