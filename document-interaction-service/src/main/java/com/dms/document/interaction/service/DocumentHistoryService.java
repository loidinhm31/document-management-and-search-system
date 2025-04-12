package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.DocumentStatisticsResponse;
import com.dms.document.interaction.dto.UserHistoryResponse;
import com.dms.document.interaction.enums.UserDocumentActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Service interface for retrieving document history and statistics.
 */
public interface DocumentHistoryService {

    /**
     * Gets statistics for a document.
     *
     * @param documentId The document ID
     * @return DocumentStatisticsResponse containing aggregated statistics
     */
    DocumentStatisticsResponse getDocumentStatistics(String documentId);

    /**
     * Gets a user's history with documents.
     *
     * @param username The username of the user
     * @param actionType Optional action type to filter by
     * @param fromDate Optional start date for filtering
     * @param toDate Optional end date for filtering
     * @param searchTerm Optional search term for filtering by document name or detail
     * @param pageable Pagination information
     * @return Page of user history responses
     */
    Page<UserHistoryResponse> getUserHistory(
            String username,
            UserDocumentActionType actionType,
            Instant fromDate,
            Instant toDate,
            String searchTerm,
            Pageable pageable);
}