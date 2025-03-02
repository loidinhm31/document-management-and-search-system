package com.dms.document.search.service;

import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.*;
import com.dms.document.search.enums.QueryType;
import com.dms.document.search.model.DocumentPreferences;
import com.dms.document.search.repository.DocumentPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.Operator;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscoverDocumentSearchService extends OpenSearchBaseService {
    private final RestHighLevelClient openSearchClient;
    private final UserClient userClient;
    private final DocumentPreferencesRepository documentPreferencesRepository;
    private final DocumentFavoriteService documentFavoriteService;

    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SUGGESTIONS = 10;

    private float getMinScore(String query, SearchContext context) {
        int length = query.trim().length();
        float baseScore;

        // Adjust base scores for Vietnamese text
        if (containsVietnameseCharacters(query)) {
            baseScore = context.queryType() == QueryType.DEFINITION ? 6.0f : 10.0f;
        } else {
            baseScore = context.queryType() == QueryType.DEFINITION ? 8.0f : 15.0f;
        }

        // Adjust score based on query length
        if (length <= 3) return baseScore * 0.4f;
        if (length <= 5) return baseScore * 0.6f;
        if (length <= 10) return baseScore * 0.8f;

        return baseScore;
    }

    private boolean containsVietnameseCharacters(String text) {
        return text.matches(".*[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ].*");
    }

    public Page<DocumentResponseDto> searchDocuments(DocumentSearchRequest request, String username) {
        try {
            ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
            if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
                throw new InvalidDataAccessResourceUsageException("User not found");
            }

            UserResponse userResponse = response.getBody();
            SearchContext searchContext = analyzeQuery(request.getSearch());

            // If search query is too short, only apply filters
            if (StringUtils.isNotEmpty(request.getSearch()) && request.getSearch().length() < MIN_SEARCH_LENGTH) {
                return Page.empty(Pageable.unpaged());
            }

            SearchRequest searchRequest = buildSearchRequest(request, searchContext, userResponse.userId());
            SearchResponse searchResponse = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);

            return processSearchResults(
                    searchResponse.getHits().getHits(),
                    Objects.nonNull(searchResponse.getHits().getTotalHits()) ? searchResponse.getHits().getTotalHits().value : 0,
                    PageRequest.of(request.getPage(), request.getSize() > 0 ? request.getSize() : 10)
            );
        } catch (IOException e) {
            log.error("Search operation failed. Request: {}, Error: {}", request, e.getMessage());
            throw new RuntimeException("Failed to perform document search", e);
        }
    }

    private SearchRequest buildSearchRequest(DocumentSearchRequest request, SearchContext context, UUID userId) {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        // Add sharing access filter
        addSharingAccessFilter(queryBuilder, userId.toString());

        // Add favorite filter if requested
        if (Boolean.TRUE.equals(request.getFavoriteOnly())) {
            documentFavoriteService.addFavoriteFilter(queryBuilder, userId);
        }

        // Add filter conditions
        addFilterConditions(queryBuilder, request.getMajor(), request.getCourseCode(), request.getLevel(), request.getCategory(), request.getTags());

        // Add search conditions if search query exists
        if (StringUtils.isNotEmpty(context.originalQuery())) {
            // Look up user preferences for document
            DocumentPreferences preferences = documentPreferencesRepository.findByUserId(userId.toString())
                    .orElse(null);

            // Add preference boosts
            if (preferences != null) {
                addBasicPreferenceBoosts(queryBuilder, preferences);
            }

            // Add search-specific query conditions
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
                .field("content", 3.0f)
                .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
                .operator(Operator.AND)
                .minimumShouldMatch("75%")
                .boost(4.0f));

        queryBuilder.minimumShouldMatch(1);
    }

    private void addGeneralSearchConditions(BoolQueryBuilder queryBuilder, SearchContext context) {
        // 1. Primary content matching - stricter
        BoolQueryBuilder contentQuery = QueryBuilders.boolQuery();

        // Case variations for content
        contentQuery.should(QueryBuilders.matchPhraseQuery("content", context.originalQuery())
                .analyzer("vietnamese_analyzer")
                .slop(1)  // Allow slight variations for Vietnamese phrases
                .boost(10.0f));
        contentQuery.should(QueryBuilders.matchPhraseQuery("content", context.lowercaseQuery())
                .analyzer("vietnamese_analyzer")
                .slop(1)
                .boost(9.0f));
        contentQuery.should(QueryBuilders.matchPhraseQuery("content", context.uppercaseQuery())
                .analyzer("vietnamese_analyzer")
                .slop(1)
                .boost(9.0f));

        // Standard content match
        contentQuery.should(QueryBuilders.matchQuery("content", context.originalQuery())
                .analyzer("vietnamese_analyzer")
                .minimumShouldMatch("60%")
                .boost(4.0f));

        queryBuilder.should(contentQuery);

        // 2. Filename matching with case variations
        BoolQueryBuilder filenameQuery = QueryBuilders.boolQuery();

        // Vietnamese-analyzed filename variations
        filenameQuery.should(QueryBuilders.matchQuery("filename.analyzed", context.originalQuery())
                .minimumShouldMatch("60%")
                .boost(5.0f));
        filenameQuery.should(QueryBuilders.matchQuery("filename.analyzed", context.lowercaseQuery())
                .minimumShouldMatch("60%")
                .boost(4.5f));
        filenameQuery.should(QueryBuilders.matchQuery("filename.analyzed", context.uppercaseQuery())
                .minimumShouldMatch("60%")
                .boost(4.5f));

        // Simple analyzer with case variations
        filenameQuery.should(QueryBuilders.matchQuery("filename.search", context.originalQuery())
                .minimumShouldMatch("60%")
                .boost(4.0f));
        filenameQuery.should(QueryBuilders.matchQuery("filename.search", context.lowercaseQuery())
                .minimumShouldMatch("60%")
                .boost(3.5f));
        filenameQuery.should(QueryBuilders.matchQuery("filename.search", context.uppercaseQuery())
                .minimumShouldMatch("60%")
                .boost(3.5f));

        // Exact matches with case variations
        filenameQuery.should(QueryBuilders.termQuery("filename.raw", context.originalQuery())
                .boost(6.0f));
        filenameQuery.should(QueryBuilders.termQuery("filename.raw", context.lowercaseQuery())
                .boost(5.5f));
        filenameQuery.should(QueryBuilders.termQuery("filename.raw", context.uppercaseQuery())
                .boost(5.5f));

        queryBuilder.should(filenameQuery);

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

            ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
            if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
                return Collections.emptyList();
            }

            UserResponse userResponse = response.getBody();

            SearchContext searchContext = analyzeQuery(request.getQuery());

            // Add suggestion search conditions
            SearchRequest searchRequest = buildSuggestionRequest(request, searchContext, userResponse.userId().toString());

            SearchResponse searchResponse = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return processSuggestionResults(searchResponse.getHits().getHits());
        } catch (Exception e) {
            log.error("Error getting suggestions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private SearchRequest buildSuggestionRequest(SuggestionRequest request, SearchContext context, String userId) {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        // Reuse access filter logic
        addSharingAccessFilter(queryBuilder, userId);

        // Reuse filter conditions but simplified
        addFilterConditions(queryBuilder, request.getMajor(), request.getCourseCode(), request.getLevel(), request.getCategory(), request.getTags());

        // Get user preferences for document personalization
        DocumentPreferences preferences = documentPreferencesRepository.findByUserId(userId)
                .orElse(null);

        // Apply stronger preference boosts (compared to search)
        // since personalization is more expected and helpful in typeahead
        if (preferences != null) {
            // Apply stronger preference boosts for suggestions (higher values than in search)
            addPreferredFieldBoost(queryBuilder, "major", preferences.getPreferredMajors(), 2.5f);
            addPreferredFieldBoost(queryBuilder, "courseCode", preferences.getPreferredCourseCodes(), 2.5f);
            addPreferredFieldBoost(queryBuilder, "courseLevel", preferences.getPreferredLevels(), 2.0f);
            addPreferredFieldBoost(queryBuilder, "category", preferences.getPreferredCategories(), 2.0f);
            addPreferredFieldBoost(queryBuilder, "tags", preferences.getPreferredTags(), 2.0f);

            // Language preferences with stronger boost
            if (CollectionUtils.isNotEmpty(preferences.getLanguagePreferences())) {
                queryBuilder.should(QueryBuilders.termsQuery("language", preferences.getLanguagePreferences())
                        .boost(2.5f));
            }
        }

        // Add lighter suggestion-specific search conditions
        if (StringUtils.isNotEmpty(context.originalQuery())) {
            addSuggestionSearchConditions(queryBuilder, request, context);
        }

        // Boost documents based on recommendation count
        addRecommendationBoost(queryBuilder);

        searchSourceBuilder.query(queryBuilder)
                .size(MAX_SUGGESTIONS)
                .trackScores(true)
                .sort(SortBuilders.scoreSort().order(SortOrder.DESC))
                .highlighter(configureSuggestionHighlightFields());

        searchRequest.source(searchSourceBuilder);
        return searchRequest;
    }

    private void addSuggestionSearchConditions(BoolQueryBuilder queryBuilder, SuggestionRequest request, SearchContext context) {
        String originalQuery = context.originalQuery();
        String lowercaseQuery = context.lowercaseQuery();
        String uppercaseQuery = context.uppercaseQuery();

        // 1. Content matching with broader acceptance
        BoolQueryBuilder contentQuery = QueryBuilders.boolQuery();

        // Phrase matching with increased slop for more flexibility
        contentQuery.should(QueryBuilders.matchPhraseQuery("content", originalQuery)
                .analyzer("vietnamese_analyzer")
                .slop(3)  // Increased slop for more flexible phrase matching
                .boost(4.0f));

        // Broad content matching with reduced minimum match
        contentQuery.should(QueryBuilders.matchQuery("content", originalQuery)
                .analyzer("vietnamese_analyzer")
                .minimumShouldMatch("30%")
                .boost(3.5f));

        // Fuzzy matching for typo tolerance
        contentQuery.should(QueryBuilders.matchQuery("content", originalQuery)
                .fuzziness(Fuzziness.AUTO)
                .prefixLength(2)
                .boost(2.5f));

        // Case variations
        contentQuery.should(QueryBuilders.matchQuery("content", lowercaseQuery)
                .analyzer("vietnamese_analyzer")
                .minimumShouldMatch("30%")
                .boost(2.0f));

        contentQuery.should(QueryBuilders.matchQuery("content", uppercaseQuery)
                .analyzer("vietnamese_analyzer")
                .minimumShouldMatch("30%")
                .boost(2.0f));

        queryBuilder.should(contentQuery);

        // 2. Enhanced filename matching
        BoolQueryBuilder filenameQuery = QueryBuilders.boolQuery();

        // Exact matches with case variations
        filenameQuery.should(QueryBuilders.termQuery("filename.raw", originalQuery)
                .boost(5.0f));

        // Analyzed filename matching
        filenameQuery.should(QueryBuilders.matchPhraseQuery("filename.analyzed", originalQuery)
                .analyzer("vietnamese_analyzer")
                .slop(2)
                .boost(4.5f));

        // Partial matches on analyzed field
        filenameQuery.should(QueryBuilders.matchQuery("filename.analyzed", originalQuery)
                .analyzer("vietnamese_analyzer")
                .minimumShouldMatch("30%")
                .boost(4.0f));

        // Simple analyzer matching
        filenameQuery.should(QueryBuilders.matchQuery("filename.search", originalQuery)
                .minimumShouldMatch("30%")
                .boost(3.5f));

        // Prefix matching for typeahead-style suggestions
        filenameQuery.should(QueryBuilders.prefixQuery("filename.raw", lowercaseQuery)
                .boost(3.0f));

        // Fuzzy matching for error tolerance
        filenameQuery.should(QueryBuilders.fuzzyQuery("filename.raw", lowercaseQuery)
                .fuzziness(Fuzziness.AUTO)
                .prefixLength(2)
                .boost(2.5f));

        // Case variations
        filenameQuery.should(QueryBuilders.matchQuery("filename.analyzed", lowercaseQuery)
                .minimumShouldMatch("30%")
                .boost(2.0f));
        filenameQuery.should(QueryBuilders.matchQuery("filename.analyzed", uppercaseQuery)
                .minimumShouldMatch("30%")
                .boost(2.0f));

        queryBuilder.should(filenameQuery);

        queryBuilder.minimumShouldMatch(1);
    }

}