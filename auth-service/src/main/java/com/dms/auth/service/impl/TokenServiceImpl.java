package com.dms.auth.service.impl;

import com.dms.auth.entity.AuthToken;
import com.dms.auth.entity.User;
import com.dms.auth.enums.TokenType;
import com.dms.auth.repository.AuthTokenRepository;
import com.dms.auth.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {
    private final AuthTokenRepository authTokenRepository;

    @Value("${spring.app.refreshTokenExpirationMs}")
    private Long refreshTokenDurationMs;

    @Value("${spring.app.accessTokenExpirationMs}")
    private Long accessTokenDurationMs;

    @Override
    @Transactional
    public AuthToken createAccessToken(User user, String jwtToken, HttpServletRequest request) {
        // Generate a hash of the token for storage
        String tokenHash = DigestUtils.sha256Hex(jwtToken);

        AuthToken authToken = AuthToken.builder()
                .user(user)
                .token(tokenHash)
                .tokenType(TokenType.ACCESS)
                .expiryDate(Instant.now().plusMillis(accessTokenDurationMs))
                .userAgent(request.getHeader("User-Agent"))
                .ipAddress(request.getRemoteAddr())
                .revoked(false)
                .createdAt(Instant.now())
                .createdBy(user.getUsername())
                .updatedBy(user.getUsername())
                .build();

        return authTokenRepository.save(authToken);
    }

    @Override
    @Transactional
    public AuthToken createRefreshToken(User user, HttpServletRequest request) {
        // Revoke any existing refresh tokens for security
        authTokenRepository.revokeAllUserTokensByType(user, TokenType.REFRESH);

        AuthToken authToken = AuthToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .tokenType(TokenType.REFRESH)
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .userAgent(request.getHeader("User-Agent"))
                .ipAddress(request.getRemoteAddr())
                .revoked(false)
                .createdAt(Instant.now())
                .createdBy(user.getUsername())
                .updatedBy(user.getUsername())
                .build();

        return authTokenRepository.save(authToken);
    }

    @Override
    public Optional<AuthToken> findByToken(String token) {
        return authTokenRepository.findByToken(token);
    }

    @Override
    public Optional<AuthToken> findByTokenAndType(String token, TokenType tokenType) {
        return authTokenRepository.findByTokenAndTokenType(token, tokenType);
    }

    @Override
    @Transactional
    public AuthToken verifyToken(AuthToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            authTokenRepository.delete(token);
            throw new RuntimeException("Token was expired. Please make a new signin request");
        }

        if (token.isRevoked()) {
            throw new RuntimeException("Token was revoked. Please sign in again");
        }

        return token;
    }

    @Override
    @Transactional
    public void revokeToken(String token, TokenType tokenType) {
        authTokenRepository.findByTokenAndTokenType(token, tokenType)
                .ifPresent((currToken) -> {
                    currToken.setRevoked(true);
                    authTokenRepository.save(currToken);
                });
    }

    @Override
    @Transactional
    public void revokeToken(String token) {
        authTokenRepository.findByToken(token)
                .ifPresent((currToken) -> {
                    currToken.setRevoked(true);
                    authTokenRepository.save(currToken);
                });
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(User user) {
        authTokenRepository.revokeAllUserTokens(user);
    }

    @Override
    @Transactional
    public void revokeAllUserTokensByType(User user, TokenType tokenType) {
        authTokenRepository.revokeAllUserTokensByType(user, tokenType);
    }

    @Override
    public List<AuthToken> findActiveTokensByUser(User user) {
        return authTokenRepository.findActiveTokensByUser(user);
    }

    @Override
    public List<AuthToken> findActiveTokensByUserAndType(User user, TokenType tokenType) {
        return authTokenRepository.findActiveTokensByUserAndType(user, tokenType);
    }

    @Override
    @Transactional
    public void removeExpiredTokens() {
        authTokenRepository.deleteAllExpiredTokens(Instant.now());
    }
}