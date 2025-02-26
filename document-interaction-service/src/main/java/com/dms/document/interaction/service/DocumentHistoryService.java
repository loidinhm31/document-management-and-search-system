package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.DocumentStatisticsResponse;
import com.dms.document.interaction.dto.UserHistoryResponse;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.model.ActionCountResult;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentHistoryService {
    private final MongoTemplate mongoTemplate;
    private final DocumentUserHistoryRepository documentUserHistoryRepository;
    private final DocumentRepository documentRepository;
    private final UserClient userClient;

    @Transactional(readOnly = true)
    public DocumentStatisticsResponse getDocumentStatistics(String documentId) {
        // Get all action counts in a single query
        List<ActionCountResult> actionCounts = documentUserHistoryRepository.getActionCountsForDocument(documentId);

        // Convert to map for easier lookup
        Map<String, Integer> countsByType = actionCounts.stream()
                .collect(Collectors.toMap(
                        ActionCountResult::getActionType,
                        ActionCountResult::getCount,
                        (a, b) -> a  // In case of duplicates, keep first value
                ));

        // Helper function to safely get count
        Function<String, Integer> getCount = type -> countsByType.getOrDefault(type, 0);

        // Calculate total interactions
        int totalInteractions = countsByType.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        // Build response DTO
        return new DocumentStatisticsResponse(
                getCount.apply(UserDocumentActionType.VIEW_DOCUMENT.name()),
                getCount.apply(UserDocumentActionType.UPDATE_DOCUMENT.name()) +
                        getCount.apply(UserDocumentActionType.UPDATE_DOCUMENT_FILE.name()),
                getCount.apply(UserDocumentActionType.DELETE_DOCUMENT.name()),
                getCount.apply(UserDocumentActionType.DOWNLOAD_FILE.name()) +
                        getCount.apply(UserDocumentActionType.DOWNLOAD_VERSION.name()),
                getCount.apply(UserDocumentActionType.REVERT_VERSION.name()),
                getCount.apply(UserDocumentActionType.SHARE.name()),
                getCount.apply(UserDocumentActionType.FAVORITE.name()),
                getCount.apply(UserDocumentActionType.COMMENT.name()),
                totalInteractions
        );
    }

    @Transactional(readOnly = true)
    public Page<UserHistoryResponse> getUserHistory(
            String username,
            UserDocumentActionType actionType,
            Instant fromDate,
            Instant toDate,
            String documentName,
            Pageable pageable) {

        // Get user info
        UserResponse user = getUserFromUsername(username);
        String userId = user.userId().toString();

        // Build dynamic query with criteria
        Query query = buildHistoryQuery(userId, actionType, fromDate, toDate, documentName);

        // Set pagination
        query.with(pageable);

        // Execute query
        List<DocumentUserHistory> histories = mongoTemplate.find(query, DocumentUserHistory.class);

        // Count query for total elements (without pagination)
        Query countQuery = buildHistoryQuery(userId, actionType, fromDate, toDate, documentName);

        // Enrich with document titles
        List<UserHistoryResponse> responses = enrichWithDocumentTitles(histories);

        // Create pageable result with count query optimization
        return PageableExecutionUtils.getPage(
                responses,
                pageable,
                () -> mongoTemplate.count(countQuery, DocumentUserHistory.class)
        );
    }

    private Query buildHistoryQuery(
            String userId,
            UserDocumentActionType actionType,
            Instant fromDate,
            Instant toDate,
            String documentName) {

        // Start with base criteria for user
        Criteria criteria = Criteria.where("userId").is(userId);

        // Add action type filter if provided
        if (actionType != null) {
            criteria.and("userDocumentActionType").is(actionType);
        }

        // Add date range filter if provided
        if (fromDate != null && toDate != null) {
            criteria.and("createdAt").gte(fromDate).lte(toDate);
        } else if (fromDate != null) {
            criteria.and("createdAt").gte(fromDate);
        } else if (toDate != null) {
            criteria.and("createdAt").lte(toDate);
        }

        // Add document name filter if provided
        if (StringUtils.isNotEmpty(documentName)) {
            List<String> documentIds = findDocumentIdsByName(documentName);
            if (!documentIds.isEmpty()) {
                criteria.and("documentId").in(documentIds);
            } else {
                // No matching documents - force empty result
                criteria.and("documentId").is("non-existent-id");
            }
        }

        // Create and return query with sort by createdAt descending
        Query query = Query.query(criteria);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        return query;
    }

    private List<UserHistoryResponse> enrichWithDocumentTitles(List<DocumentUserHistory> histories) {
        if (histories.isEmpty()) {
            return Collections.emptyList();
        }

        // Extract unique document IDs
        Set<String> documentIds = histories.stream()
                .map(DocumentUserHistory::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Batch fetch documents
        Map<String, String> documentTitles;
        if (!documentIds.isEmpty()) {
            documentTitles = documentRepository.findByIdIn(new ArrayList<>(documentIds)).stream()
                    .collect(Collectors.toMap(
                            DocumentInformation::getId,
                            DocumentInformation::getFilename,
                            (existing, replacement) -> existing
                    ));
        } else {
            documentTitles = new HashMap<>();
        }

        // Map to response DTOs
        return histories.stream()
                .map(history -> mapToResponse(history, documentTitles.getOrDefault(history.getDocumentId(), "Unknown Document")))
                .collect(Collectors.toList());
    }

    private UserHistoryResponse mapToResponse(DocumentUserHistory history, String documentTitle) {
        return new UserHistoryResponse(
                history.getId(),
                history.getUserDocumentActionType(),
                history.getDocumentId(),
                documentTitle,
                history.getDetail(),
                history.getVersion(),
                history.getCreatedAt()
        );
    }

    private UserResponse getUserFromUsername(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        return response.getBody();
    }

    private List<String> findDocumentIdsByName(String documentName) {
        Criteria criteria = Criteria.where("filename").regex(documentName, "i")
                .and("deleted").ne(true);

        Query query = Query.query(criteria);
        query.fields().include("_id");

        List<DocumentInformation> documents = mongoTemplate.find(query, DocumentInformation.class);

        return documents.stream()
                .map(DocumentInformation::getId)
                .collect(Collectors.toList());
    }
}