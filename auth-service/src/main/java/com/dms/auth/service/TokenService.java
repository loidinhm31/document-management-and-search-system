package com.dms.auth.service;

import com.dms.auth.entity.AuthToken;
import com.dms.auth.entity.User;
import com.dms.auth.enums.TokenType;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Optional;

public interface TokenService {
    /**
     * Creates a new access token for the user
     */
    AuthToken createAccessToken(User user, String jwtToken, HttpServletRequest request);

    /**
     * Creates a new refresh token for the user
     */
    AuthToken createRefreshToken(User user, HttpServletRequest request);

    /**
     * Finds a token by its token string
     */
    Optional<AuthToken> findByToken(String token);

    /**
     * Finds a token by its token string and type
     */
    Optional<AuthToken> findByTokenAndType(String token, TokenType tokenType);

    /**
     * Verifies that a token is not expired or revoked
     */
    AuthToken verifyToken(AuthToken token);

    /**
     * Revokes a specific token
     */
    void revokeToken(String token, TokenType tokenType);

    /**
     * Revokes a specific token (any type)
     */
    void revokeToken(String token);

    /**
     * Revokes all tokens for a user
     */
    void revokeAllUserTokens(User user);

    /**
     * Revokes all tokens of a specific type for a user
     */
    void revokeAllUserTokensByType(User user, TokenType tokenType);

    /**
     * Finds all active tokens for a user
     */
    List<AuthToken> findActiveTokensByUser(User user);

    /**
     * Finds all active tokens of a specific type for a user
     */
    List<AuthToken> findActiveTokensByUserAndType(User user, TokenType tokenType);

    /**
     * Removes all expired tokens from the database
     */
    void removeExpiredTokens();
}