package com.dms.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "otp_verifications")
public class OtpVerification extends BaseEntity<String> {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String otp;

    @Column(nullable = false)
    private String email;

    @Column(name = "expiry_time", nullable = false)
    private Instant expiryTime;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "is_validated", nullable = false)
    private boolean validated;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public boolean isExpired() {
        return Instant.now().isAfter(expiryTime);
    }

    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    public boolean hasExceededMaxAttempts() {
        return attemptCount >= 5;
    }
}