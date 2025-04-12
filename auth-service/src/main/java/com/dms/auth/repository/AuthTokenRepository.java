package com.dms.auth.repository;

import com.dms.auth.entity.AuthToken;
import com.dms.auth.entity.User;
import com.dms.auth.enums.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
    Optional<AuthToken> findByToken(String token);

    Optional<AuthToken> findByTokenAndTokenType(String token, TokenType tokenType);

    @Query("SELECT t FROM AuthToken t WHERE t.user = :user AND t.revoked = false AND t.tokenType = :tokenType")
    List<AuthToken> findActiveTokensByUserAndType(User user, TokenType tokenType);

    @Query("SELECT t FROM AuthToken t WHERE t.user = :user AND t.revoked = false")
    List<AuthToken> findActiveTokensByUser(User user);

    @Modifying
    @Query("UPDATE AuthToken t SET t.revoked = true WHERE t.user = :user")
    void revokeAllUserTokens(User user);

    @Modifying
    @Query("UPDATE AuthToken t SET t.revoked = true WHERE t.user = :user AND t.tokenType = :tokenType")
    void revokeAllUserTokensByType(User user, TokenType tokenType);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiryDate < :now")
    void deleteAllExpiredTokens(Instant now);
}