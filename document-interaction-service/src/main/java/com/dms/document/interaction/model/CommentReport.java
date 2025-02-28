package com.dms.document.interaction.model;

import com.dms.document.interaction.enums.CommentReportStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comment_reports",
        uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "user_id"}))
@Data
public class CommentReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "comment_id", nullable = false, insertable = false, updatable = false)
    private Long commentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "report_type_code", nullable = false)
    private String reportTypeCode;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "processed")
    private Boolean processed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CommentReportStatus status;

    @Column(name = "times")
    private Integer times;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private DocumentComment comment;
}