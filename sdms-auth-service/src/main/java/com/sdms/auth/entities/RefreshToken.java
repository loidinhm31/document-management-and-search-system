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
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity<String> {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiryDate;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;
}