package com.dms.document.interaction.model;


import com.dms.document.interaction.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_reports",
        uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "user_id"}))
@Data
public class DocumentReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "report_type_code", nullable = false)
    private String reportTypeCode;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_at")
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

}