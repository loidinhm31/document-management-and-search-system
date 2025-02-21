package com.dms.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
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
    private LocalDateTime expiryTime;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "is_validated", nullable = false)
    private boolean validated;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }

    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    public boolean hasExceededMaxAttempts() {
        return attemptCount >= 5;
    }
}