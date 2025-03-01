package com.dms.document.interaction.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_recommendations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "mentor_id"}))
@Data
public class DocumentRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;

    @Column(name = "created_at")
    private Instant createdAt;

}