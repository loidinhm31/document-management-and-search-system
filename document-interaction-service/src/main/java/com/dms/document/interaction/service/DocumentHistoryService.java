package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.DocumentStatisticsResponse;
import com.dms.document.interaction.dto.UserHistoryResponse;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.model.projection.ActionCountResult;
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
            String searchTerm,
            Pageable pageable) {

        // Get user info
        UserResponse user = getUserFromUsername(username);
        String userId = user.userId().toString();

        // Find document IDs matching the search term in filename (if provided)
        List<String> matchingDocumentIds = StringUtils.isNotEmpty(searchTerm)
                ? findDocumentIdsByName(searchTerm)
                : Collections.emptyList();

        // Build dynamic query with criteria for history
        Query query = buildHistoryQuery(userId, actionType, fromDate, toDate, searchTerm, matchingDocumentIds);

        // Set pagination and sorting
        query.with(pageable);

        // Execute query
        List<DocumentUserHistory> histories = mongoTemplate.find(query, DocumentUserHistory.class);

        // Count query for total elements (without pagination)
        Query countQuery = buildHistoryQuery(userId, actionType, fromDate, toDate, searchTerm, matchingDocumentIds);

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
            String searchTerm,
            List<String> matchingDocumentIds) {

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

        // Add search term filter for filename (via document IDs) and detail
        if (StringUtils.isNotEmpty(searchTerm)) {
            List<Criteria> orCriteria = new ArrayList<>();

            // Match documents by IDs from filename search
            if (!matchingDocumentIds.isEmpty()) {
                orCriteria.add(Criteria.where("documentId").in(matchingDocumentIds));
            }

            // Match detail field with regex (case-insensitive)
            orCriteria.add(Criteria.where("detail").regex(searchTerm, "i"));

            criteria.andOperator(new Criteria().orOperator(orCriteria.toArray(new Criteria[0])));
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

    private List<String> findDocumentIdsByName(String searchTerm) {
        Criteria criteria = Criteria.where("filename").regex(searchTerm, "i")
                .and("deleted").ne(true);

        Query query = Query.query(criteria);
        query.fields().include("_id");

        // Use raw Document to avoid instantiation issues
        return mongoTemplate.find(query, org.bson.Document.class, "documents")
                .stream()
                .map(doc -> doc.get("_id").toString())
                .collect(Collectors.toList());
    }
}