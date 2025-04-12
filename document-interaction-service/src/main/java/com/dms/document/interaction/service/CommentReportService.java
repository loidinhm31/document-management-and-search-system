package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.CommentReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing comment reports.
 */
public interface CommentReportService {

    /**
     * Creates a new comment report.
     *
     * @param documentId The document ID containing the comment
     * @param commentId The ID of the comment being reported
     * @param request The report request containing details
     * @param username The username of the reporting user
     * @return CommentReportResponse containing the created report details
     */
    CommentReportResponse createReport(String documentId, Long commentId, CommentReportRequest request, String username);

    /**
     * Retrieves a user's report for a specific comment.
     *
     * @param documentId The document ID
     * @param commentId The comment ID
     * @param username The username of the user
     * @return Optional containing the report response if found
     */
    Optional<CommentReportResponse> getUserReport(String documentId, Long commentId, String username);

    /**
     * Retrieves all available report types.
     *
     * @return List of report type responses
     */
    List<ReportTypeResponse> getReportTypes();

    /**
     * Resolves a comment report by updating its status.
     *
     * @param commentId The comment ID
     * @param newStatus The new status to set
     * @param adminUsername The username of the admin resolving the report
     */
    void resolveCommentReport(Long commentId, CommentReportStatus newStatus, String adminUsername);

    /**
     * Retrieves a paginated list of comment reports for admin view.
     *
     * @param fromDate Optional start date for filtering
     * @param toDate Optional end date for filtering
     * @param commentContent Optional content text to search for
     * @param reportTypeCode Optional report type code to filter by
     * @param status Optional status to filter by
     * @param pageable Pagination information
     * @return Paginated list of admin comment report responses
     */
    Page<AdminCommentReportResponse> getAdminCommentReports(
            Instant fromDate,
            Instant toDate,
            String commentContent,
            String reportTypeCode,
            CommentReportStatus status,
            Pageable pageable);

    /**
     * Gets detailed reports for a specific comment.
     *
     * @param commentId The comment ID
     * @param status Optional status to filter by
     * @return List of detailed comment report responses
     */
    List<CommentReportDetailResponse> getCommentReportsByCommentId(Long commentId, CommentReportStatus status);
}