package com.dms.auth.repository;

import com.dms.auth.entity.PasswordResetToken;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenAndUsed(String toke, boolean used);

    @Query("SELECT t FROM PasswordResetToken t WHERE t.user.email = :email AND t.used = false ORDER BY t.createdAt DESC LIMIT 1")
    Optional<PasswordResetToken> findLatestByUserEmail(@Param("email") String email);

    @Query(value = "SELECT t.* FROM password_reset_token t WHERE t.user_id = :userId", nativeQuery = true)
    List<PasswordResetToken> findAllByUserId(UUID userId);
}
