package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.CommentReport;
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
    boolean existsByCommentIdAndUserId(Long commentId, UUID userId);
    Optional<CommentReport> findByDocumentIdAndCommentIdAndUserId(String documentId, Long commentId, UUID userId);

    @Query("SELECT cr FROM CommentReport cr WHERE cr.userId = :userId AND cr.documentId = :documentId")
    List<CommentReport> findReportsByUserAndDocument(@Param("userId") UUID userId, @Param("documentId") String documentId);

    @Query("""
           SELECT cr FROM CommentReport cr
           JOIN DocumentComment dc ON cr.commentId = dc.id
           WHERE (:commentContent IS NULL OR LOWER(dc.content) LIKE LOWER(CONCAT('%', :commentContent, '%')))
           AND (:reportTypeCode IS NULL OR cr.reportTypeCode = :reportTypeCode)
           AND (:createdFrom IS NULL OR cr.createdAt >= :createdFrom)
           AND (:createdTo IS NULL OR cr.createdAt <= :createdTo)
           AND (:resolved IS NULL OR cr.resolved = :resolved)
           ORDER BY cr.createdAt DESC
           """)
    Page<CommentReport> findAllWithFilters(
            @Param("commentContent") String commentContent,
            @Param("reportTypeCode") String reportTypeCode,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            @Param("resolved") Boolean resolved,
            Pageable pageable);
}