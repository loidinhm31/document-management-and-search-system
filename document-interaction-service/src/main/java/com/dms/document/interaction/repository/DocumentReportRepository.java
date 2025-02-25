package com.dms.document.interaction.repository;

import com.dms.document.interaction.enums.ReportStatus;
import com.dms.document.interaction.model.DocumentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentReportRepository extends JpaRepository<DocumentReport, Long> {
    boolean existsByDocumentIdAndUserId(String documentId, UUID userId);
    Optional<DocumentReport> findByDocumentIdAndUserId(String documentId, UUID userId);

    @Modifying
    @Query("UPDATE DocumentReport r SET r.status = :status, r.resolvedBy = :resolvedBy, " +
           "r.resolvedAt = :resolvedAt WHERE r.documentId = :documentId")
    void updateStatusForDocument(String documentId, ReportStatus status,
                                 UUID resolvedBy, Instant resolvedAt);
}