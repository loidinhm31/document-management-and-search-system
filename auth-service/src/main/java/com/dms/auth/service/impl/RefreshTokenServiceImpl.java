package com.dms.auth.service.impl;

import com.dms.auth.entity.RefreshToken;
import com.dms.auth.entity.User;
import com.dms.auth.repository.RefreshTokenRepository;
import com.dms.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${spring.app.refreshTokenExpirationMs}")
    private Long refreshTokenDurationMs;

    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user, HttpServletRequest request) {
        // Revoke any existing refresh tokens for security
        refreshTokenRepository.revokeAllUserTokens(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .userAgent(request.getHeader("User-Agent"))
                .ipAddress(request.getRemoteAddr())
                .createdAt(Instant.now())
                .createdBy(user.getUsername())
                .updatedBy(user.getUsername())
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Override
    @Transactional
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }

        // Add check for revoked flag
        if (token.isRevoked()) {
            throw new RuntimeException("Refresh token was revoked. Please sign in again");
        }

        return token;
    }

    @Override
    @Transactional
    public void revokeToken(String token) {
        refreshTokenRepository.findByToken(token)
                .ifPresent((currToken) -> {
                    currToken.setRevoked(true);
                    refreshTokenRepository.save(currToken);
                });
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllUserTokens(user);
    }

    @Override
    public List<RefreshToken> findActiveTokensByUser(User user) {
        return refreshTokenRepository.findActiveTokensByUser(user);
    }

    @Override
    @Transactional
    public void removeExpiredTokens() {
        refreshTokenRepository.deleteAllExpiredTokens(Instant.now());
    }
}