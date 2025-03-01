package com.dms.document.interaction.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_notes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "mentor_id"}))
@Data
public class DocumentNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;

    @Column(name = "content", nullable = false, length = 200)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "edited")
    private boolean edited;
}