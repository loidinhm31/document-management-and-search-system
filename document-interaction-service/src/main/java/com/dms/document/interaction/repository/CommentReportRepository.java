package com.dms.document.interaction.repository;

import com.dms.document.interaction.enums.CommentReportStatus;
import com.dms.document.interaction.model.CommentReport;
import com.dms.document.interaction.model.projection.CommentReportProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
    boolean existsByCommentIdAndUserIdAndProcessed(Long commentId, UUID userId, boolean processed);

    Optional<CommentReport> findByDocumentIdAndCommentIdAndUserIdAndProcessed(String documentId, Long commentId, UUID userId, boolean processed);

    @Query("SELECT cr FROM CommentReport cr WHERE cr.userId = :userId AND cr.documentId = :documentId AND cr.processed = :processed")
    List<CommentReport> findReportsByUserAndDocument(@Param("userId") UUID userId, @Param("documentId") String documentId, @Param("processed") boolean processed);

    List<CommentReport> findByCommentIdAndStatus(Long commentId, CommentReportStatus status);

    List<CommentReport> findByCommentIdAndProcessed(Long commentId, boolean processed);

    @Query(value = """
            SELECT 
                cr.comment_id AS commentId,
                cr.document_id AS documentId,
                dc.content AS commentContent,
                cr.processed AS processed,
                cr.status AS status,
                COUNT(cr.id) AS reportCount,
                (SELECT sub.user_id FROM comment_reports sub 
                 WHERE sub.comment_id = cr.comment_id 
                 AND sub.status = cr.status
                 ORDER BY sub.created_at DESC LIMIT 1) AS reporterId,
                dc.user_id AS commentUserId,
                (SELECT sub.updated_by FROM comment_reports sub 
                 WHERE sub.comment_id = cr.comment_id 
                 AND sub.status = cr.status
                 ORDER BY sub.updated_at DESC NULLS LAST LIMIT 1) AS updatedBy,
                (SELECT sub.report_type_code FROM comment_reports sub 
                 WHERE sub.comment_id = cr.comment_id 
                 AND sub.status = cr.status
                 ORDER BY sub.created_at DESC LIMIT 1) AS reportTypeCode,
                (SELECT sub.description FROM comment_reports sub 
                 WHERE sub.comment_id = cr.comment_id 
                 AND sub.status = cr.status
                 ORDER BY sub.created_at DESC LIMIT 1) AS description,
                MAX(cr.created_at) AS createdAt,
                MAX(cr.updated_at) AS updatedAt
            FROM comment_reports cr
            JOIN document_comments dc ON cr.comment_id = dc.id
            WHERE (:commentContent IS NULL OR dc.content::text LIKE CONCAT('%', :commentContent, '%'))
            AND (:reportTypeCode IS NULL OR cr.report_type_code = :reportTypeCode)
            AND (:status IS NULL OR cr.status = :status)
            AND (CAST(:fromDate AS date) IS NULL OR DATE(cr.created_at) >= DATE(:fromDate))
            AND (CAST(:toDate AS date) IS NULL OR DATE(cr.created_at) <= DATE(:toDate))
            GROUP BY cr.comment_id, cr.document_id, cr.processed, cr.status, dc.content, dc.user_id
            ORDER BY MAX(cr.created_at) DESC
            """, nativeQuery = true)
    Page<CommentReportProjection> findCommentReportsGroupedByProcessed(
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("commentContent") String commentContent,
            @Param("reportTypeCode") String reportTypeCode,
            @Param("status") String status,
            Pageable pageable);

    @Query(value = """
            SELECT COUNT(DISTINCT CONCAT(cr.comment_id, '_', COALESCE(cr.processed, false), '_', cr.status))
            FROM comment_reports cr
            JOIN document_comments dc ON cr.comment_id = dc.id
            WHERE (:commentContent IS NULL OR dc.content::text LIKE CONCAT('%', :commentContent, '%'))
            AND (:reportTypeCode IS NULL OR cr.report_type_code = :reportTypeCode)
            AND (:status IS NULL OR cr.status = :status)
            AND (CAST(:fromDate AS date) IS NULL OR DATE(cr.created_at) >= DATE(:fromDate))
            AND (CAST(:toDate AS date) IS NULL OR DATE(cr.created_at) <= DATE(:toDate))
            """, nativeQuery = true)
    long countCommentReportsGroupedByProcessed(
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("commentContent") String commentContent,
            @Param("reportTypeCode") String reportTypeCode,
            @Param("status") String status);
}