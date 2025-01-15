package com.sdms.auth.entities;

import jakarta.persistence.*;
import lombok.*;
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
public class PasswordResetToken extends BaseEntity<String> {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Instant expiryDate;

    private boolean used;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public PasswordResetToken(String token, Instant expiryDate, User user) {
        this.token = token;
        this.expiryDate = expiryDate;
        this.user = user;
    }
}
