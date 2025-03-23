package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.DocumentReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentReportService {

    /**
     * Creates a new document report
     *
     * @param documentId Document identifier
     * @param request Report details
     * @param username Username of the reporter
     * @return Report response with details
     */
    ReportResponse createReport(String documentId, ReportRequest request, String username);

    /**
     * Updates the status of a document report
     *
     * @param documentId Document identifier
     * @param newStatus New status to set
     * @param username Username of the admin user making the update
     */
    void updateReportStatus(String documentId, DocumentReportStatus newStatus, String username);

    /**
     * Retrieves a user's report for a specific document
     *
     * @param documentId Document identifier
     * @param username Username of the user
     * @return Optional containing the report if found
     */
    Optional<ReportResponse> getUserReport(String documentId, String username);

    /**
     * Retrieves all available report types
     *
     * @return List of report type responses
     */
    List<ReportTypeResponse> getReportTypes();

    /**
     * Retrieves a paginated list of document reports for admin view
     *
     * @param documentTitle Filter by document title (optional)
     * @param fromDate Filter by start date (optional)
     * @param toDate Filter by end date (optional)
     * @param status Filter by report status (optional)
     * @param reportTypeCode Filter by report type (optional)
     * @param pageable Pagination information
     * @return Page of admin document report responses
     */
    Page<AdminDocumentReportResponse> getAdminDocumentReports(
            String documentTitle,
            Instant fromDate,
            Instant toDate,
            DocumentReportStatus status,
            String reportTypeCode,
            Pageable pageable);

    /**
     * Retrieves detailed information about reports for a specific document
     *
     * @param documentId Document identifier
     * @param status Filter by report status (optional)
     * @return List of document report details
     */
    List<DocumentReportDetail> getDocumentReportDetails(String documentId, DocumentReportStatus status);
}