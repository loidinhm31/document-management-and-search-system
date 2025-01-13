package com.example.demo2.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String action;

    @Column(length = 1000)
    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "status")
    private String status; // SUCCESS, FAILURE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}