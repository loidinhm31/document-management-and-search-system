package com.dms.processor.repository;

import com.dms.processor.model.DocumentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface DocumentReportRepository extends JpaRepository<DocumentReport, Long> {
    @Query("SELECT dr.userId FROM DocumentReport dr WHERE dr.documentId = :documentId AND dr.processed = :processed")
    Set<UUID> findReporterUserIdsByDocumentIdAndProcessed(@Param("documentId") String documentId, @Param("processed") boolean processed);
}