package com.dms.document.search.service.impl;

import com.dms.document.search.repository.DocumentFavoriteRepository;
import com.dms.document.search.service.DocumentFavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentFavoriteServiceImpl implements DocumentFavoriteService {
    private final DocumentFavoriteRepository documentFavoriteRepository;

    private static final int MAX_FAVORITE_IDS = 1000;

    @Override
    public void addFavoriteFilter(BoolQueryBuilder queryBuilder, UUID userId) {
        Set<String> favoriteDocIds = getFavoriteDocumentIds(userId);

        if (favoriteDocIds.isEmpty()) {
            // If user has no favorites but favoriteOnly is true, ensure no results returned
            queryBuilder.filter(QueryBuilders.termQuery("_id", "no_matching_document_id"));
            return;
        }

        // Check if user have too many favorites (exceeding OpenSearch terms query limits)
        if (hasTooManyFavorites(userId)) {
            // Use multiple should clauses with terms query batches
            handleLargeFavoritesList(queryBuilder, favoriteDocIds);
        } else {
            // Standard approach for reasonable number of favorites
            queryBuilder.filter(QueryBuilders.termsQuery("_id", favoriteDocIds));
        }
    }

    private void handleLargeFavoritesList(BoolQueryBuilder queryBuilder, Set<String> favoriteDocIds) {
        // Convert to list for partitioning
        List<String> docIdList = new ArrayList<>(favoriteDocIds);

        // Max terms per clause
        final int BATCH_SIZE = 1000;

        BoolQueryBuilder batchQuery = QueryBuilders.boolQuery();

        // Split into batches
        for (int i = 0; i < docIdList.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, docIdList.size());
            List<String> batch = docIdList.subList(i, endIndex);

            batchQuery.should(QueryBuilders.termsQuery("_id", batch));
        }

        // Require at least one batch to match
        batchQuery.minimumShouldMatch(1);
        queryBuilder.filter(batchQuery);
    }

    private Set<String> getFavoriteDocumentIds(UUID userId) {
        try {
            return documentFavoriteRepository.findRecentFavoriteDocumentIdsByUserId(userId, MAX_FAVORITE_IDS);
        } catch (Exception e) {
            log.error("Error retrieving favorite document IDs for user {}: {}", userId, e.getMessage());
            return Set.of(); // Return empty set on error
        }
    }

    private boolean hasTooManyFavorites(UUID userId) {
        // Perform a count query which is more efficient than retrieving all IDs
        return documentFavoriteRepository.findDocumentIdsByUserId(userId).size() > MAX_FAVORITE_IDS;
    }
}
