package com.dms.document.interaction.repository;

import com.dms.document.interaction.enums.ReportStatus;
import com.dms.document.interaction.model.DocumentReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface DocumentReportRepository extends JpaRepository<DocumentReport, Long> {
    boolean existsByDocumentIdAndUserId(String documentId, UUID userId);

    Optional<DocumentReport> findByDocumentIdAndUserId(String documentId, UUID userId);

    @Modifying
    @Query("UPDATE DocumentReport r SET r.status = :status, r.resolvedBy = :resolvedBy, " +
           "r.resolvedAt = :resolvedAt WHERE r.documentId = :documentId")
    void updateStatusForDocument(String documentId, ReportStatus status,
                                 UUID resolvedBy, Instant resolvedAt);

    List<DocumentReport> findByDocumentIdIn(Collection<String> documentIds);

    @Query(value = """
            SELECT DISTINCT r.document_id FROM document_reports r
            WHERE (CAST(:status AS varchar) IS NULL OR r.status = :status)
            AND (CAST(:fromDate AS date) IS NULL OR DATE(r.created_at) >= DATE(:fromDate))
            AND (CAST(:toDate AS date) IS NULL OR DATE(r.created_at) <= DATE(:toDate))
            AND (CAST(:reportTypeCode AS varchar) IS NULL OR r.report_type_code = :reportTypeCode)
            ORDER BY r.document_id
            """,
            nativeQuery = true)
    Page<String> findDistinctDocumentIdsWithFilters(
            @Param("status") String status,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("reportTypeCode") String reportTypeCode,
            Pageable pageable);

    @Query(value = """
            SELECT COUNT(DISTINCT r.document_id) FROM document_reports r
            WHERE (CAST(:status AS varchar) IS NULL OR r.status = :status)
            AND (CAST(:fromDate AS date) IS NULL OR DATE(r.created_at) >= DATE(:fromDate))
            AND (CAST(:toDate AS date) IS NULL OR DATE(r.created_at) <= DATE(:toDate))
            AND (CAST(:reportTypeCode AS varchar) IS NULL OR r.report_type_code = :reportTypeCode)
            """,
            nativeQuery = true)
    long countDistinctDocumentIdsWithFilters(
            @Param("status") String status,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("reportTypeCode") String reportTypeCode);
}