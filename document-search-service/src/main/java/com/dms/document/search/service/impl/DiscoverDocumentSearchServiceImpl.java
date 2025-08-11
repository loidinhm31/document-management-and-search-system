package com.dms.document.search.service.impl;

import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.*;
import com.dms.document.search.enums.AppRole;
import com.dms.document.search.enums.QueryType;
import com.dms.document.search.model.DocumentPreferences;
import com.dms.document.search.repository.DocumentPreferencesRepository;
import com.dms.document.search.service.DocumentFavoriteService;
import com.dms.document.search.service.DocumentSearchService;
import com.dms.document.search.service.LanguageDetectionService;
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
public class DiscoverDocumentSearchServiceImpl extends OpenSearchBaseService implements DocumentSearchService {
    private final RestHighLevelClient openSearchClient;
    private final UserClient userClient;
    private final DocumentPreferencesRepository documentPreferencesRepository;
    private final DocumentFavoriteService documentFavoriteService;
    private final LanguageDetectionService languageDetectionService;

    private static final int MIN_SEARCH_LENGTH = 2;

    private float getMinScore(String query, SearchContext context, String detectedLanguage) {
        int length = query.trim().length();
        float baseScore = switch (detectedLanguage) {
            case "vi" -> // Vietnamese
                    context.queryType() == QueryType.DEFINITION ? 6.0f : 10.0f;
            case "ko" -> // Korean
                    context.queryType() == QueryType.DEFINITION ? 5.0f : 8.0f;
            default -> // English and others
                    context.queryType() == QueryType.DEFINITION ? 8.0f : 15.0f;
        };

        // Adjust base score based on detected language and query type

        // Adjust score based on query length
        if (length <= 3) return baseScore * 0.4f;
        if (length <= 5) return baseScore * 0.6f;
        if (length <= 10) return baseScore * 0.8f;

        return baseScore;
    }

    @Override
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

            SearchRequest searchRequest = buildSearchRequest(request, searchContext, userResponse.userId(), userResponse.role().roleName());
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

    private SearchRequest buildSearchRequest(DocumentSearchRequest request, SearchContext context, UUID userId, AppRole userRole) {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        // Add a sharing access filter
        addSharingAccessFilter(queryBuilder, userId.toString(), userRole);

        // Add a favorite filter if requested
        if (Boolean.TRUE.equals(request.getFavoriteOnly())) {
            documentFavoriteService.addFavoriteFilter(queryBuilder, userId);
        }

        // Boost documents based on recommendation count
        addRecommendationBoost(queryBuilder);

        // Boost documents based on favorite count
        addFavoriteCountBoost(queryBuilder);

        // Add filter conditions
        addFilterConditions(queryBuilder, request.getMajors(), request.getCourseCodes(), request.getLevel(), request.getCategories(), request.getTags());

        // Add search conditions if search query exists
        if (StringUtils.isNotEmpty(context.originalQuery())) {
            // Detect language of search query
            String detectedLanguage = languageDetectionService.detectLanguage(context.originalQuery());
            log.debug("Detected language '{}' for query: '{}'", detectedLanguage, context.originalQuery());

            // Look up user preferences for document
            DocumentPreferences preferences = documentPreferencesRepository.findByUserId(userId.toString())
                    .orElse(null);

            // Add preference boosts
            if (preferences != null) {
                addBasicPreferenceBoosts(queryBuilder, preferences);
            }

            // Add language-aware search conditions
            if (context.queryType() == QueryType.DEFINITION) {
                addDefinitionSearchConditions(queryBuilder, context, detectedLanguage);
            } else {
                addGeneralSearchConditions(queryBuilder, context, detectedLanguage);
            }

            // Set minimum score with language-aware adjustment
            searchSourceBuilder.minScore(getMinScore(request.getSearch(), context, detectedLanguage));
        }

        searchSourceBuilder.query(queryBuilder);

        // Add sorting with proper score handling
        addSorting(searchSourceBuilder, request);

        // Add pagination
        searchSourceBuilder.from(request.getPage() * request.getSize());
        searchSourceBuilder.size(request.getSize() > 0 ? request.getSize() : 10);

        // Add highlighting
        searchSourceBuilder.highlighter(configureHighlightFields(context));

        // Exclude content field
        searchSourceBuilder.fetchSource(new String[]{"*"}, new String[]{"content"});

        searchRequest.source(searchSourceBuilder);
        return searchRequest;
    }

    private void addDefinitionSearchConditions(BoolQueryBuilder queryBuilder, SearchContext context, String detectedLanguage) {
        String query = context.originalQuery();

        // Higher boost for phrase matches in content with language-specific field
        switch (detectedLanguage) {
            case "ko": // Korean
                queryBuilder.should(QueryBuilders.matchPhraseQuery("content.korean", query)
                        .boost(18.0f));
                queryBuilder.should(QueryBuilders.matchQuery("content.korean", query)
                        .boost(12.0f));
                break;
            case "vi": // Vietnamese
                queryBuilder.should(QueryBuilders.matchPhraseQuery("content.vietnamese", query)
                        .boost(15.0f));
                queryBuilder.should(QueryBuilders.matchQuery("content.vietnamese", query)
                        .analyzer("vietnamese_analyzer")
                        .boost(10.0f));
                break;
            default: // English and others
                queryBuilder.should(QueryBuilders.matchPhraseQuery("content", query)
                        .boost(15.0f));
                queryBuilder.should(QueryBuilders.matchQuery("content", query)
                        .analyzer("universal_analyzer")
                        .boost(10.0f));
                break;
        }

        // Cross-field matching
        queryBuilder.should(QueryBuilders.multiMatchQuery(query)
                .field("filename", 4.0f)
                .field("content", 3.0f)
                .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
                .operator(Operator.AND)
                .minimumShouldMatch("75%")
                .boost(4.0f));

        queryBuilder.minimumShouldMatch(1);
    }

    private void addGeneralSearchConditions(BoolQueryBuilder queryBuilder, SearchContext context, String detectedLanguage) {
        String originalQuery = context.originalQuery();
        String lowercaseQuery = context.lowercaseQuery();
        String uppercaseQuery = context.uppercaseQuery();

        // Primary content matching with language-specific handling
        BoolQueryBuilder contentQuery = QueryBuilders.boolQuery();

        switch (detectedLanguage) {
            case "ko": // Korean
                // Korean-optimized field with higher boost
                contentQuery.should(QueryBuilders.matchPhraseQuery("content.korean", originalQuery)
                        .slop(1)
                        .boost(12.0f));
                contentQuery.should(QueryBuilders.matchQuery("content.korean", originalQuery)
                        .minimumShouldMatch("60%")
                        .boost(8.0f));

                // Fallback to universal field
                contentQuery.should(QueryBuilders.matchQuery("content", originalQuery)
                        .analyzer("universal_analyzer")
                        .minimumShouldMatch("70%")
                        .boost(4.0f));
                break;

            case "vi": // Vietnamese
                // Vietnamese-optimized field with higher boost
                contentQuery.should(QueryBuilders.matchPhraseQuery("content.vietnamese", originalQuery)
                        .analyzer("vietnamese_analyzer")
                        .slop(1)
                        .boost(10.0f));
                contentQuery.should(QueryBuilders.matchPhraseQuery("content.vietnamese", lowercaseQuery)
                        .analyzer("vietnamese_analyzer")
                        .slop(1)
                        .boost(9.0f));
                contentQuery.should(QueryBuilders.matchPhraseQuery("content.vietnamese", uppercaseQuery)
                        .analyzer("vietnamese_analyzer")
                        .slop(1)
                        .boost(9.0f));
                contentQuery.should(QueryBuilders.matchQuery("content.vietnamese", originalQuery)
                        .analyzer("vietnamese_analyzer")
                        .minimumShouldMatch("70%")
                        .boost(4.0f));
                break;

            default: // English and others
                // Universal field for English and other languages
                contentQuery.should(QueryBuilders.matchPhraseQuery("content", originalQuery)
                        .analyzer("universal_analyzer")
                        .slop(1)
                        .boost(10.0f));
                contentQuery.should(QueryBuilders.matchQuery("content", originalQuery)
                        .analyzer("universal_analyzer")
                        .minimumShouldMatch("70%")
                        .boost(4.0f));

                // Case variations
                contentQuery.should(QueryBuilders.matchPhraseQuery("content", lowercaseQuery)
                        .analyzer("universal_analyzer")
                        .slop(1)
                        .boost(9.0f));
                contentQuery.should(QueryBuilders.matchPhraseQuery("content", uppercaseQuery)
                        .analyzer("universal_analyzer")
                        .slop(1)
                        .boost(9.0f));
                break;
        }

        queryBuilder.should(contentQuery);

        // Filename matching with language-aware handling
        BoolQueryBuilder filenameQuery = QueryBuilders.boolQuery();

        switch (detectedLanguage) {
            case "ko": // Korean
                filenameQuery.should(QueryBuilders.matchQuery("filename.korean", originalQuery)
                        .minimumShouldMatch("60%")
                        .boost(6.0f));
                break;
            case "vi": // Vietnamese
                filenameQuery.should(QueryBuilders.matchQuery("filename.vietnamese", originalQuery)
                        .minimumShouldMatch("60%")
                        .boost(5.0f));
                break;
        }

        // Universal filename matching for all languages
        filenameQuery.should(QueryBuilders.matchQuery("filename", originalQuery)
                .minimumShouldMatch("60%")
                .boost(5.0f));
        filenameQuery.should(QueryBuilders.matchQuery("filename.search", originalQuery)
                .minimumShouldMatch("60%")
                .boost(4.0f));

        // Exact matches with case variations
        filenameQuery.should(QueryBuilders.termQuery("filename.raw", originalQuery).boost(6.0f));
        filenameQuery.should(QueryBuilders.termQuery("filename.raw", lowercaseQuery).boost(5.5f));
        filenameQuery.should(QueryBuilders.termQuery("filename.raw", uppercaseQuery).boost(5.5f));

        queryBuilder.should(filenameQuery);
        queryBuilder.minimumShouldMatch(1);
    }

    private void addSorting(SearchSourceBuilder searchSourceBuilder, DocumentSearchRequest request) {
        // Always track scores for both search queries and filter-based queries
        searchSourceBuilder.trackScores(true);

        boolean hasExplicitSort = StringUtils.isNotBlank(request.getSortField());

        // If there's an explicit sort field requested
        if (hasExplicitSort) {
            SortOrder sortOrder = SortOrder.DESC;
            if (StringUtils.isNotBlank(request.getSortDirection())) {
                sortOrder = SortOrder.valueOf(request.getSortDirection().toUpperCase());
            }
            String sortField = getSortableFieldName(request.getSortField());

            // First sort by the explicit field
            searchSourceBuilder.sort(SortBuilders.fieldSort(sortField).order(sortOrder));

            // Then sort by score as a secondary criterion
            searchSourceBuilder.sort(SortBuilders.scoreSort().order(SortOrder.DESC));
        } else {
            // If no explicit sort field, prioritize score-based sorting
            searchSourceBuilder.sort(SortBuilders.scoreSort().order(SortOrder.DESC));

            // Add a secondary sort by createdAt for consistent ordering when scores are equal
            searchSourceBuilder.sort(SortBuilders.fieldSort("createdAt").order(SortOrder.DESC));
        }
    }

    private String getSortableFieldName(String field) {
        return switch (field) {
            case "filename" -> "filename.lowercase";
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

    @Override
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
            SearchRequest searchRequest = buildSuggestionRequest(request, searchContext, userResponse.userId().toString(), userResponse.role().roleName());

            SearchResponse searchResponse = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);
            return processSuggestionResults(searchResponse.getHits().getHits());
        } catch (Exception e) {
            log.error("Error getting suggestions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private SearchRequest buildSuggestionRequest(SuggestionRequest request, SearchContext context, String userId, AppRole userRole) {
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        // Reuse access filter logic
        addSharingAccessFilter(queryBuilder, userId, userRole);

        // Reuse filter conditions but simplified
        addFilterConditions(queryBuilder, request.getMajors(), request.getCourseCodes(), request.getLevel(), request.getCategories(), request.getTags());

        // Get user preferences for document personalization
        DocumentPreferences preferences = documentPreferencesRepository.findByUserId(userId)
                .orElse(null);

        // Apply stronger preference boosts (compared to search)
        // since personalization is more expected and helpful in typeahead
        if (preferences != null) {
            // Apply stronger preference boosts for suggestions (higher values than in search)
            addPreferredFieldBoost(queryBuilder, "majors", preferences.getPreferredMajors(), 2.5f);
            addPreferredFieldBoost(queryBuilder, "courseCodes", preferences.getPreferredCourseCodes(), 2.5f);
            addPreferredFieldBoost(queryBuilder, "courseLevel", preferences.getPreferredLevels(), 2.0f);
            addPreferredFieldBoost(queryBuilder, "categories", preferences.getPreferredCategories(), 2.0f);
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

        // Boost documents based on favorite count
        addFavoriteCountBoost(queryBuilder);

        searchSourceBuilder.query(queryBuilder)
                .size(MAX_SUGGESTIONS)
                .trackScores(true)
                .sort(SortBuilders.scoreSort().order(SortOrder.DESC))
                .highlighter(configureSuggestionHighlightFields())
                .fetchSource(new String[]{"*"}, new String[]{"content"});

        searchRequest.source(searchSourceBuilder);
        return searchRequest;
    }

    private void addSuggestionSearchConditions(BoolQueryBuilder queryBuilder, SuggestionRequest request, SearchContext context) {
        String originalQuery = context.originalQuery();
        String lowercaseQuery = context.lowercaseQuery();
        String uppercaseQuery = context.uppercaseQuery();
        String detectedLanguage = languageDetectionService.detectLanguage(originalQuery);

        // Content matching with broader acceptance
        BoolQueryBuilder contentQuery = QueryBuilders.boolQuery();

        switch (detectedLanguage) {
            case "ko": // Korean
                contentQuery.should(QueryBuilders.matchPhraseQuery("content.korean", originalQuery)
                        .slop(3)
                        .boost(5.0f));
                contentQuery.should(QueryBuilders.matchQuery("content.korean", originalQuery)
                        .minimumShouldMatch("30%")
                        .boost(4.0f));
                contentQuery.should(QueryBuilders.matchQuery("content.korean", originalQuery)
                        .fuzziness(Fuzziness.AUTO)
                        .prefixLength(1)
                        .boost(3.0f));
                break;

            case "vi": // Vietnamese
                contentQuery.should(QueryBuilders.matchPhraseQuery("content.vietnamese", originalQuery)
                        .analyzer("vietnamese_analyzer")
                        .slop(3)
                        .boost(4.0f));
                contentQuery.should(QueryBuilders.matchQuery("content.vietnamese", originalQuery)
                        .analyzer("vietnamese_analyzer")
                        .minimumShouldMatch("30%")
                        .boost(3.5f));

                // Case variations
                contentQuery.should(QueryBuilders.matchQuery("content", lowercaseQuery)
                        .analyzer("vietnamese_analyzer")
                        .minimumShouldMatch("30%")
                        .boost(2.0f));

                contentQuery.should(QueryBuilders.matchQuery("content", uppercaseQuery)
                        .analyzer("vietnamese_analyzer")
                        .minimumShouldMatch("30%")
                        .boost(2.0f));
                break;

            default: // English and others
                contentQuery.should(QueryBuilders.matchPhraseQuery("content", originalQuery)
                        .analyzer("universal_analyzer")
                        .slop(3)
                        .boost(4.0f));
                contentQuery.should(QueryBuilders.matchQuery("content", originalQuery)
                        .analyzer("universal_analyzer")
                        .minimumShouldMatch("30%")
                        .boost(3.5f));
                break;
        }

        // Fuzzy matching for typo tolerance
        contentQuery.should(QueryBuilders.matchQuery("content", originalQuery)
                .fuzziness(Fuzziness.AUTO)
                .prefixLength(2)
                .boost(2.5f));

        queryBuilder.should(contentQuery);

        // Filename matching with language awareness
        BoolQueryBuilder filenameQuery = QueryBuilders.boolQuery();

        switch (detectedLanguage) {
            case "ko":
                filenameQuery.should(QueryBuilders.matchPhraseQuery("filename.korean", originalQuery)
                        .slop(2)
                        .boost(5.5f));
                filenameQuery.should(QueryBuilders.matchQuery("filename.korean", originalQuery)
                        .minimumShouldMatch("30%")
                        .boost(5.0f));
                break;
            case "vi":
                filenameQuery.should(QueryBuilders.matchPhraseQuery("filename.vietnamese", originalQuery)
                        .analyzer("vietnamese_analyzer")
                        .slop(2)
                        .boost(4.5f));
                filenameQuery.should(QueryBuilders.matchQuery("filename.vietnamese", originalQuery)
                        .analyzer("vietnamese_analyzer")
                        .minimumShouldMatch("30%")
                        .boost(4.0f));
                break;
        }

        // Exact matches with case variations
        filenameQuery.should(QueryBuilders.termQuery("filename.raw", originalQuery)
                .boost(5.0f));
        filenameQuery.should(QueryBuilders.matchQuery("filename", originalQuery)
                .minimumShouldMatch("30%")
                .boost(4.0f));
        filenameQuery.should(QueryBuilders.matchQuery("filename.search", originalQuery)
                .minimumShouldMatch("30%")
                .boost(3.5f));
        filenameQuery.should(QueryBuilders.prefixQuery("filename.raw", lowercaseQuery)
                .boost(3.0f));
        filenameQuery.should(QueryBuilders.fuzzyQuery("filename.raw", uppercaseQuery)
                .fuzziness(Fuzziness.AUTO)
                .prefixLength(2)
                .boost(2.5f));

        queryBuilder.should(filenameQuery);
        queryBuilder.minimumShouldMatch(1);
    }
}