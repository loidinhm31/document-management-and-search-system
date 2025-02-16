package com.dms.document.search.service;

import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.*;
import com.dms.document.search.enums.QueryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.Operator;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightField;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscoverDocumentSearchService extends OpenSearchBaseService {
    private final RestHighLevelClient openSearchClient;
    private final UserClient userClient;

    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SUGGESTIONS = 10;

    private float getMinScore(String query, SearchContext context) {
        int length = query.trim().length();
        float baseScore = context.queryType() == QueryType.DEFINITION ? 8.0f : 15.0f;

        if (length <= 3) return baseScore * 0.5f;
        if (length <= 5) return baseScore * 0.65f;
        if (length <= 10) return baseScore * 0.85f;

        return baseScore;
    }

    public Page<DocumentResponseDto> searchDocuments(DocumentSearchRequest request, String username) {
        try {
            ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
            if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
                throw new InvalidDataAccessResourceUsageException("User not found");
            }

            UserDto userDto = response.getBody();
            SearchContext searchContext = analyzeQuery(request.getSearch());

            // If search query is too short, only apply filters
            if (StringUtils.isNotEmpty(request.getSearch()) && request.getSearch().length() < MIN_SEARCH_LENGTH) {
                return Page.empty(Pageable.unpaged());
            }

            SearchRequest searchRequest = buildSearchRequest(request, searchContext, userDto.getUserId().toString());
            SearchResponse searchResponse = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);

            return processSearchResults(
                    searchResponse.getHits().getHits(),
                    searchResponse.getHits().getTotalHits().value,
                    PageRequest.of(request.getPage(), request.getSize() > 0 ? request.getSize() : 10)
            );
        } catch (IOException e) {
            log.error("Search operation failed. Request: {}, Error: {}", request, e.getMessage());
            throw new RuntimeException("Failed to perform document search", e);
        }
    }

    private SearchRequest buildSearchRequest(DocumentSearchRequest request, SearchContext context, String userId) {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        // Add sharing access filter
        addSharingAccessFilter(queryBuilder, userId);

        // Add filter conditions
        addFilterConditions(queryBuilder, request);

        // Add search conditions if search query exists
        if (StringUtils.isNotEmpty(context.originalQuery())) {
            if (context.queryType() == QueryType.DEFINITION) {
                addDefinitionSearchConditions(queryBuilder, context);
            } else {
                addGeneralSearchConditions(queryBuilder, context);
            }

            // Set minimum score for search queries
            searchSourceBuilder.minScore(getMinScore(request.getSearch(), context));
        }

        searchSourceBuilder.query(queryBuilder);

        // Add sorting with proper score handling
        addSorting(searchSourceBuilder, request);

        // Add pagination
        searchSourceBuilder.from(request.getPage() * request.getSize());
        searchSourceBuilder.size(request.getSize() > 0 ? request.getSize() : 10);

        // Add highlighting
        searchSourceBuilder.highlighter(configureHighlightFields(context));

        searchRequest.source(searchSourceBuilder);
        return searchRequest;
    }

    private void addFilterConditions(BoolQueryBuilder queryBuilder, DocumentSearchRequest request) {
        if (StringUtils.isNotBlank(request.getMajor())) {
            queryBuilder.filter(QueryBuilders.termQuery("major", request.getMajor()));
        }

        if (StringUtils.isNotBlank(request.getLevel())) {
            queryBuilder.filter(QueryBuilders.termQuery("courseLevel", request.getLevel()));
        }

        if (StringUtils.isNotBlank(request.getCategory())) {
            queryBuilder.filter(QueryBuilders.termQuery("category", request.getCategory()));
        }

        if (CollectionUtils.isNotEmpty(request.getTags())) {
            queryBuilder.filter(QueryBuilders.termsQuery("tags", request.getTags()));
        }
    }

    private void addDefinitionSearchConditions(BoolQueryBuilder queryBuilder, SearchContext context) {
        // Higher boost for phrase matches in content
        queryBuilder.should(QueryBuilders.matchPhraseQuery("content", context.originalQuery())
                .boost(15.0f));

        // Search in analyzed fields for better Vietnamese text handling
        queryBuilder.should(QueryBuilders.matchQuery("content", context.originalQuery())
                .analyzer("vietnamese_analyzer")
                .boost(10.0f));

        // Cross-field matching
        queryBuilder.should(QueryBuilders.multiMatchQuery(context.originalQuery())
                .field("filename", 4.0f)
                .field("content", 2.0f)
                .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
                .operator(Operator.AND)
                .minimumShouldMatch("75%")
                .boost(4.0f));

        queryBuilder.minimumShouldMatch(1);
    }

    private void addGeneralSearchConditions(BoolQueryBuilder queryBuilder, SearchContext context) {
        // Content search with Vietnamese analysis
        queryBuilder.should(QueryBuilders.matchQuery("content", context.originalQuery())
                .analyzer("vietnamese_analyzer")
                .boost(2.0f));

        // Fuzzy search on content
        queryBuilder.should(QueryBuilders.matchQuery("content", context.originalQuery())
                .fuzziness("AUTO")
                .boost(1.0f));

        // Multiple case variations for exact matches
        BoolQueryBuilder exactMatchesBuilder = QueryBuilders.boolQuery();

        // Original case
        exactMatchesBuilder.should(QueryBuilders.termQuery("content.keyword", context.originalQuery())
                .boost(3.0f));

        // Lowercase variation
        exactMatchesBuilder.should(QueryBuilders.termQuery("content.keyword", context.lowercaseQuery())
                .boost(2.5f));

        // Uppercase variation
        exactMatchesBuilder.should(QueryBuilders.termQuery("content.keyword", context.uppercaseQuery())
                .boost(2.5f));

        queryBuilder.should(exactMatchesBuilder);

        // Filename search with different analyzers
        queryBuilder.should(QueryBuilders.matchQuery("filename.analyzed", context.originalQuery())
                .boost(4.0f));

        queryBuilder.should(QueryBuilders.matchQuery("filename.search", context.originalQuery())
                .boost(3.0f));

        // Exact matches
        BoolQueryBuilder exactMatchBuilder = QueryBuilders.boolQuery();
        exactMatchBuilder.should(QueryBuilders.termQuery("filename.raw", context.originalQuery()).boost(5.0f));
        exactMatchBuilder.should(QueryBuilders.termQuery("filename.raw", context.lowercaseQuery()).boost(4.5f));
        exactMatchBuilder.should(QueryBuilders.termQuery("filename.raw", context.uppercaseQuery()).boost(4.5f));
        queryBuilder.should(exactMatchBuilder);

        queryBuilder.minimumShouldMatch(1);
    }

    private void addSorting(SearchSourceBuilder searchSourceBuilder, DocumentSearchRequest request) {
        // For search queries, prioritize relevance
        if (StringUtils.isNotEmpty(request.getSearch())) {
            searchSourceBuilder.trackScores(true);

            // If there's an explicit sort field requested
            if (StringUtils.isNotBlank(request.getSortField())) {
                SortOrder sortOrder = SortOrder.DESC;
                if (StringUtils.isNotBlank(request.getSortDirection())) {
                    sortOrder = SortOrder.valueOf(request.getSortDirection().toUpperCase());
                }
                String sortField = getSortableFieldName(request.getSortField());

                // Add both score and field sorting
                searchSourceBuilder
                        .sort(SortBuilders.fieldSort(sortField).order(sortOrder));
            } else {
                // If no explicit sort field, sort by score only
                searchSourceBuilder.sort(SortBuilders.scoreSort().order(SortOrder.DESC));
            }
            return;
        }

        // For non-search queries, use standard sorting
        SortOrder sortOrder = SortOrder.DESC;
        if (StringUtils.isNotBlank(request.getSortDirection())) {
            sortOrder = SortOrder.valueOf(request.getSortDirection().toUpperCase());
        }

        String sortField = StringUtils.isNotBlank(request.getSortField()) ?
                getSortableFieldName(request.getSortField()) : "createdAt";

        searchSourceBuilder.sort(SortBuilders.fieldSort(sortField).order(sortOrder));
    }

    private String getSortableFieldName(String field) {
        return switch (field) {
            case "filename" -> "filename.raw";
            case "content" -> "content.keyword";
            case "created_at", "createdAt" -> "createdAt";
            default -> field;
        };
    }

    private SearchContext analyzeQuery(String query) {
        String cleanQuery = query != null ? query.trim() : "";
        boolean isProbableDefinition = cleanQuery.toLowerCase().split("\\s+").length <= 3;

        return new SearchContext(
                isProbableDefinition ? QueryType.DEFINITION : QueryType.GENERAL,
                cleanQuery,
                cleanQuery.toUpperCase(),
                cleanQuery.toLowerCase()
        );
    }

    public List<String> getSuggestions(SuggestionRequest request, String username) {
        try {
            if (request.getQuery().length() < MIN_SEARCH_LENGTH) {
                return Collections.emptyList();
            }

            ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
            if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
                return Collections.emptyList();
            }

            UserDto userDto = response.getBody();

            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

            // Add sharing access filter
            addSharingAccessFilter(queryBuilder, userDto.getUserId().toString());

            // Add filter conditions
            if (StringUtils.isNotBlank(request.getMajor())) {
                queryBuilder.filter(QueryBuilders.termQuery("major", request.getMajor()));
            }
            if (StringUtils.isNotBlank(request.getLevel())) {
                queryBuilder.filter(QueryBuilders.termQuery("courseLevel", request.getLevel()));
            }
            if (StringUtils.isNotBlank(request.getCategory())) {
                queryBuilder.filter(QueryBuilders.termQuery("category", request.getCategory()));
            }
            if (CollectionUtils.isNotEmpty(request.getTags())) {
                queryBuilder.filter(QueryBuilders.termsQuery("tags", request.getTags()));
            }

            // Add suggestion search conditions
            BoolQueryBuilder suggestionQuery = QueryBuilders.boolQuery();

            // Vietnamese-aware filename match
            suggestionQuery.should(QueryBuilders.matchQuery("filename.analyzed", request.getQuery()).boost(4.0f));

            // Basic filename search
            suggestionQuery.should(QueryBuilders.matchQuery("filename.search", request.getQuery()).boost(3.0f));

            // Exact filename match
            suggestionQuery.should(QueryBuilders.termQuery("filename.raw", request.getQuery()).boost(5.0f));

            // Content search with Vietnamese analysis
            suggestionQuery.should(QueryBuilders.matchQuery("content", request.getQuery())
                    .analyzer("vietnamese_analyzer")
                    .boost(5.0f));

            queryBuilder.must(suggestionQuery);

            searchSourceBuilder.query(queryBuilder)
                    .size(MAX_SUGGESTIONS)
                    .trackScores(true)
                    .sort(SortBuilders.scoreSort().order(SortOrder.DESC))
                    .highlighter(configureSuggestionHighlightFields());

            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);

            return processSuggestionResults(searchResponse.getHits().getHits());
        } catch (Exception e) {
            log.error("Error getting suggestions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> processSuggestionResults(SearchHit[] hits) {
        return Arrays.stream(hits)
                .map(hit -> {
                    Map<String, HighlightField> highlightFields = hit.getHighlightFields();

                    // First try to get filename highlights
                    if (highlightFields.containsKey("filename.analyzed")) {
                        return highlightFields.get("filename.analyzed").fragments()[0].string();
                    }

                    if (highlightFields.containsKey("filename.search")) {
                        return highlightFields.get("filename.search").fragments()[0].string();
                    }

                    // Then try content highlights
                    if (highlightFields.containsKey("content")) {
                        return highlightFields.get("content").fragments()[0].string();
                    }

                    // Fallback to original filename
                    return (String) hit.getSourceAsMap().get("filename");
                })
                .distinct()
                .collect(Collectors.toList());
    }
}