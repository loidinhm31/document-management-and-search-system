package com.dms.processor.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "document_favorites")
public class DocumentFavorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "created_at")
    private Instant createdAt;
}