package com.dms.processor.repository;

import com.dms.processor.enums.ReportStatus;
import com.dms.processor.model.DocumentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DocumentReportRepository extends JpaRepository<DocumentReport, Long> {
    List<DocumentReport> findByDocumentId(String documentId);

    List<DocumentReport> findByDocumentIdAndStatus(String documentId, ReportStatus status);

    @Query("SELECT dr.userId FROM DocumentReport dr WHERE dr.documentId = :documentId")
    Set<UUID> findReporterUserIdsByDocumentId(@Param("documentId") String documentId);
}