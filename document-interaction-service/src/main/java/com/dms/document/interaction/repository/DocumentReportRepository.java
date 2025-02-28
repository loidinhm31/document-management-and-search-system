package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentReport;
import com.dms.document.interaction.model.projection.DocumentReportProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface DocumentReportRepository extends JpaRepository<DocumentReport, Long> {
    boolean existsByDocumentIdAndUserIdAndProcessed(String documentId, UUID userId, boolean processed);

    Optional<DocumentReport> findByDocumentIdAndUserIdAndProcessed(String documentId, UUID userId, boolean processed);

    List<DocumentReport> findByDocumentIdAndProcessed(String documentId, boolean processed);

    @Query(value = """
            SELECT 
                r.document_id AS documentId, 
                r.processed AS processed, 
                r.status AS status,
                COUNT(r.id) AS reportCount,
                (SELECT sub.updated_by FROM document_reports sub 
                 WHERE sub.document_id = r.document_id 
                 AND COALESCE(sub.processed, false) = COALESCE(r.processed, false)
                 AND sub.status = r.status
                 ORDER BY sub.updated_at DESC NULLS LAST LIMIT 1) AS updatedBy,
                MAX(r.updated_at) AS updatedAt
            FROM document_reports r
            WHERE (:status IS NULL OR r.status = :status)
            AND (CAST(:fromDate AS date) IS NULL OR DATE(r.created_at) >= DATE(:fromDate))
            AND (CAST(:toDate AS date) IS NULL OR DATE(r.created_at) <= DATE(:toDate))
            AND (:reportTypeCode IS NULL OR r.report_type_code = :reportTypeCode)
            GROUP BY r.document_id, r.processed, r.status
            ORDER BY r.document_id, r.processed DESC
            """,
            nativeQuery = true)
    Page<DocumentReportProjection> findDocumentReportsGroupedByProcessed(
            @Param("status") String status,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("reportTypeCode") String reportTypeCode,
            Pageable pageable);

    @Query(value = """
            SELECT COUNT(DISTINCT CONCAT(r.document_id, '_', COALESCE(r.processed, false), '_', r.status))
            FROM document_reports r
            WHERE (:status IS NULL OR r.status = :status)
            AND (CAST(:fromDate AS date) IS NULL OR DATE(r.created_at) >= DATE(:fromDate))
            AND (CAST(:toDate AS date) IS NULL OR DATE(r.created_at) <= DATE(:toDate))
            AND (:reportTypeCode IS NULL OR r.report_type_code = :reportTypeCode)
            """,
            nativeQuery = true)
    long countDocumentReportsGroupedByProcessed(
            @Param("status") String status,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("reportTypeCode") String reportTypeCode);
}