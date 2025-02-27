package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.CommentReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
    boolean existsByCommentIdAndUserId(Long commentId, UUID userId);
    Optional<CommentReport> findByDocumentIdAndCommentIdAndUserId(String documentId, Long commentId, UUID userId);

    @Query("SELECT cr FROM CommentReport cr WHERE cr.userId = :userId AND cr.documentId = :documentId")
    List<CommentReport> findReportsByUserAndDocument(@Param("userId") UUID userId, @Param("documentId") String documentId);

    @Query(value = """
            SELECT cr.* FROM comment_reports cr
            JOIN document_comments dc ON cr.comment_id = dc.id
            WHERE (:commentContent IS NULL OR dc.content::text LIKE CONCAT('%', :commentContent, '%'))
            AND (:reportTypeCode IS NULL OR cr.report_type_code = :reportTypeCode)
            AND (:resolved IS NULL OR cr.resolved = :resolved)
            AND (CAST(:fromDate AS date) IS NULL OR DATE(cr.created_at) >= DATE(:fromDate))
            AND (CAST(:toDate AS date) IS NULL OR DATE(cr.created_at) <= DATE(:toDate))
            ORDER BY cr.created_at DESC
            """, nativeQuery = true)
    Page<CommentReport> findAllWithFilters(
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("commentContent") String commentContent,
            @Param("reportTypeCode") String reportTypeCode,
            @Param("resolved") Boolean resolved,
            Pageable pageable);
}