package com.dms.processor.repository;

import com.dms.processor.model.CommentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
    @Query("SELECT cr.userId FROM CommentReport cr WHERE cr.commentId = :commentId")
    Set<UUID> findReporterUserIdsByCommentId(@Param("commentId") Long commentId);
}