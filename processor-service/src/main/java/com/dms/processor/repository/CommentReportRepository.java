package com.dms.processor.repository;

import com.dms.processor.model.CommentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
    @Query("SELECT cr.userId FROM CommentReport cr WHERE cr.commentId = :commentId AND cr.processed = :processed")
    Set<UUID> findReporterUserIdsByCommentIdAndProcessed(@Param("commentId") Long commentId, @Param("processed") boolean processed);

    List<CommentReport> findByCommentIdAndProcessed(@Param("commentId") Long commentId, @Param("processed") boolean processed);
}