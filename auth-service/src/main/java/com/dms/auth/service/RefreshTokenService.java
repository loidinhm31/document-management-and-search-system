package com.dms.auth.service;

import com.dms.auth.entity.RefreshToken;
import com.dms.auth.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenService {
    /**
     * Creates a new refresh token for the user
     *
     * @param user    the user for whom to create the token
     * @param request HTTP request containing user agent and IP information
     * @return the created refresh token
     */
    RefreshToken createRefreshToken(User user, HttpServletRequest request);

    /**
     * Finds a refresh token by its token string
     *
     * @param token the token string to search for
     * @return an Optional containing the refresh token if found
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Verifies that a refresh token is not expired
     *
     * @param token the refresh token to verify
     * @return the refresh token if valid
     * @throws RuntimeException if the token is expired or revoked
     */
    RefreshToken verifyExpiration(RefreshToken token);

    /**
     * Revokes a specific refresh token
     *
     * @param token the token string to revoke
     */
    void revokeToken(String token);

    /**
     * Revokes all refresh tokens for a user
     *
     * @param user the user whose tokens should be revoked
     */
    void revokeAllUserTokens(User user);

    /**
     * Finds all active refresh tokens for a user
     *
     * @param user the user whose tokens to find
     * @return a list of active refresh tokens
     */
    List<RefreshToken> findActiveTokensByUser(User user);

    /**
     * Removes all expired tokens from the database
     */
    void removeExpiredTokens();
}