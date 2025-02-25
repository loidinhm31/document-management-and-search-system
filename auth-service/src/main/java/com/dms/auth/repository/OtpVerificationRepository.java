package com.dms.auth.repository;

import com.dms.auth.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
    Optional<OtpVerification> findByEmailAndValidatedFalse(String email);

    @Query("SELECT o FROM OtpVerification o WHERE o.user.username = :username AND o.validated = false")
    Optional<OtpVerification> findValidOtpByUsername(String username);

    boolean existsByEmailAndLockedUntilAfter(String email, Instant now);
}