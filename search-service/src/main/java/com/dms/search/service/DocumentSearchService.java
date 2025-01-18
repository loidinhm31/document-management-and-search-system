package com.dms.search.service;

import com.dms.search.elasticsearch.DocumentIndex;
import com.dms.search.entity.User;
import com.dms.search.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSearchService {
    private final ElasticsearchOperations elasticsearchOperations;
    private final UserRepository userRepository;

    public Page<DocumentIndex> searchDocuments(String searchQuery, String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidDataAccessResourceUsageException("User not found"));

        try {
            // Create criteria for basic filtering
            Criteria userCriteria = new Criteria("userId").is(user.getUserId());

            // Create search criteria with boosting and fuzzy matching
            Criteria searchCriteria = new Criteria()
                    .or(new Criteria("filename").boost(3.0f).fuzzy(searchQuery))
                    .or(new Criteria("content").boost(1.0f).fuzzy(searchQuery));

            // Combine criteria
            Criteria finalCriteria = new Criteria().and(userCriteria).and(searchCriteria);

            // Build query with criteria and sorting
            Query query = new CriteriaQuery(finalCriteria)
                    .addSort(Sort.by(Sort.Direction.DESC, "_score"))
                    .setPageable(pageable);

            // Execute search
            SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                    query,
                    DocumentIndex.class
            );

            // Process results
            List<DocumentIndex> documents = searchHits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(
                    documents,
                    pageable,
                    searchHits.getTotalHits()
            );

        } catch (Exception e) {
            log.error("Error performing document search", e);
            throw new RuntimeException("Failed to perform document search", e);
        }
    }

    public List<String> getSuggestions(String prefix, String userId) {
        try {
            // Create criteria for prefix matching
            Criteria criteria = new Criteria("userId").is(userId)
                    .and(new Criteria("filename").startsWith(prefix.toLowerCase()));

            // Create query with pagination
            Query query = new CriteriaQuery(criteria)
                    .setPageable(PageRequest.of(0, 5, Sort.by("filename").ascending()));

            // Execute search
            SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                    query,
                    DocumentIndex.class
            );

            // Return unique suggestions
            return searchHits.getSearchHits().stream()
                    .map(hit -> hit.getContent().getFilename())
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting suggestions", e);
            return Collections.emptyList();
        }
    }
}