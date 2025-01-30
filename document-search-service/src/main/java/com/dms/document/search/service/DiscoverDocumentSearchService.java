package com.dms.document.search.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.dms.document.search.client.UserClient;
import com.dms.document.dto.*;
import com.dms.document.search.dto.*;
import com.dms.document.search.elasticsearch.DocumentIndex;
import com.dms.document.search.enums.QueryType;
import com.dms.document.search.enums.SharingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscoverDocumentSearchService {
    private final ElasticsearchOperations elasticsearchOperations;
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

    public Page<DocumentResponseDto> searchDocuments(
            DocumentSearchRequest request,
            String username,
            Pageable pageable) {
        try {
            ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
            if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
                throw new InvalidDataAccessResourceUsageException("User not found");
            }

            UserDto userDto = response.getBody();
            SearchContext searchContext = analyzeQuery(request.getSearch());

            // If search query is too short, only apply filters
            if (StringUtils.isNotEmpty(request.getSearch()) && request.getSearch().length() < MIN_SEARCH_LENGTH) {
                return Page.empty(pageable);
            }

            Query query = buildSearchQuery(request, searchContext, userDto.getUserId().toString());
            List<HighlightField> highlightFields = configureHighlightFields(searchContext);
            Highlight highlight = new Highlight(highlightFields);

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withHighlightQuery(new HighlightQuery(highlight, DocumentIndex.class))
                    .withPageable(pageable)
                    .withTrackScores(true)
                    .withMinScore(getMinScore(request.getSearch(), searchContext))
                    .build();

            SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                    nativeQuery,
                    DocumentIndex.class
            );

            return processSearchResults(searchHits, pageable);
        } catch (Exception e) {
            log.error("Search operation failed. Request: {}, Error: {}", request, e.getMessage());
            throw new RuntimeException("Failed to perform document search", e);
        }
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

            Query query = buildSuggestionQuery(request, userDto.getUserId().toString());
            List<HighlightField> highlightFields = configureSuggestionHighlightFields();
            Highlight highlight = new Highlight(highlightFields);

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withHighlightQuery(new HighlightQuery(highlight, DocumentIndex.class))
                    .withPageable(PageRequest.of(0, MAX_SUGGESTIONS))
                    .withTrackScores(true)
                    .build();

            SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                    nativeQuery,
                    DocumentIndex.class
            );

            return processSuggestionResults(searchHits);
        } catch (Exception e) {
            log.error("Error getting suggestions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Query buildSearchQuery(DocumentSearchRequest request, SearchContext context, String userId) {
        BoolQuery.Builder queryBuilder = new BoolQuery.Builder();

        // Add sharing access filter
        addSharingAccessFilter(queryBuilder, userId);

        // Add filter conditions based on request parameters
        addFilterConditions(
                queryBuilder,
                request.getMajor(),
                request.getLevel(),
                request.getCategory(),
                request.getTags()
        );

        // Add search conditions if search query exists
        if (StringUtils.isNotEmpty(context.originalQuery())) {
            if (context.queryType() == QueryType.DEFINITION) {
                addDefinitionSearchConditions(queryBuilder, context);
            } else {
                addGeneralSearchConditions(queryBuilder, context);
            }
        }

        return queryBuilder.build()._toQuery();
    }

    private Query buildSuggestionQuery(SuggestionRequest request, String userId) {
        BoolQuery.Builder queryBuilder = new BoolQuery.Builder();

        // Add sharing access filter
        addSharingAccessFilter(queryBuilder, userId);

        // Add filter conditions based on request parameters
        addFilterConditions(
                queryBuilder,
                request.getMajor(),
                request.getLevel(),
                request.getCategory(),
                request.getTags()
        );

        // Add suggestion search conditions
        queryBuilder.must(must -> must
                .bool(b -> {
                    // Vietnamese-aware filename match
                    b.should(should -> should
                            .match(m -> m
                                    .field("filename.analyzed")
                                    .query(request.getQuery())
                                    .boost(4.0f)));

                    // Basic filename search
                    b.should(should -> should
                            .match(m -> m
                                    .field("filename.search")
                                    .query(request.getQuery())
                                    .boost(3.0f)));

                    // Exact filename match
                    b.should(should -> should
                            .term(t -> t
                                    .field("filename.raw")
                                    .value(request.getQuery())
                                    .boost(5.0f)));

                    // Content search with Vietnamese analysis
                    b.should(should -> should
                            .match(m -> m
                                    .field("content")
                                    .analyzer("vietnamese_analyzer")
                                    .query(request.getQuery())
                                    .boost(5.0f)));

                    return b.minimumShouldMatch("1");
                }));

        return queryBuilder.build()._toQuery();
    }

    private void addFilterConditions(BoolQuery.Builder queryBuilder, String major, String level, String category, Set<String> tags) {
        if (StringUtils.isNotBlank(major)) {
            queryBuilder.filter(f -> f
                    .term(t -> t
                            .field("major")
                            .value(major)));
        }

        if (StringUtils.isNotBlank(level)) {
            queryBuilder.filter(f -> f
                    .term(t -> t
                            .field("course_level")
                            .value(level)));
        }

        if (StringUtils.isNotBlank(category)) {
            queryBuilder.filter(f -> f
                    .term(t -> t
                            .field("category")
                            .value(category)));
        }

        if (CollectionUtils.isNotEmpty(tags)) {
            queryBuilder.filter(f -> f
                    .terms(t -> t
                            .field("tags")
                            .terms(tt -> tt
                                    .value(tags.stream()
                                            .map(FieldValue::of)
                                            .collect(Collectors.toList())))));
        }
    }

    private void addDefinitionSearchConditions(BoolQuery.Builder queryBuilder, SearchContext context) {
        // Build query based on context
        queryBuilder.must(must -> must
                .bool(b -> {
                    // Higher boost for phrase matches in content
                    b.should(should -> should
                            .matchPhrase(mp -> mp
                                    .field("content")
                                    .query(context.originalQuery())
                                    .boost(15.0f)));

                    // Search in analyzed fields for better Vietnamese text handling
                    b.should(should -> should
                            .match(m -> m
                                    .field("content")
                                    .analyzer("vietnamese_analyzer")
                                    .query(context.originalQuery())
                                    .boost(10.0f)));

                    // Cross-field matching
                    b.should(should -> should
                            .multiMatch(mm -> mm
                                    .query(context.originalQuery())
                                    .fields("filename^4", "content^2")
                                    .type(TextQueryType.CrossFields)
                                    .operator(Operator.And)
                                    .minimumShouldMatch("75%")
                                    .boost(4.0f)));

                    return b.minimumShouldMatch("1");
                }));
    }

    private void addGeneralSearchConditions(BoolQuery.Builder queryBuilder, SearchContext context) {
        queryBuilder.must(must -> must
                .bool(b -> {
                    // Vietnamese analyzed content search (case-insensitive)
                    b.should(should -> should
                            .match(m -> m
                                    .field("content")
                                    .analyzer("vietnamese_analyzer")
                                    .query(context.originalQuery())
                                    .boost(2.0f)));

                    // Fuzzy search on base content (case-insensitive)
                    b.should(should -> should
                            .match(m -> m
                                    .field("content")
                                    .query(context.originalQuery())
                                    .fuzziness("AUTO")
                                    .operator(Operator.Or)
                                    .boost(1.0f)));

                    // Multiple case variations for exact matches
                    b.should(should -> should
                            .bool(exactMatch -> {
                                // Original case
                                exactMatch.should(s -> s
                                        .term(t -> t
                                                .field("content.keyword")
                                                .value(context.originalQuery())
                                                .boost(3.0f)));

                                // Lowercase variation
                                exactMatch.should(s -> s
                                        .term(t -> t
                                                .field("content.keyword")
                                                .value(context.lowercaseQuery())
                                                .boost(2.5f)));

                                // Uppercase variation
                                exactMatch.should(s -> s
                                        .term(t -> t
                                                .field("content.keyword")
                                                .value(context.uppercaseQuery())
                                                .boost(2.5f)));

                                return exactMatch;
                            }));

                    // Filename search with different analyzers
                    b.should(should -> should
                            .match(m -> m
                                    .field("filename.analyzed")
                                    .query(context.originalQuery())
                                    .boost(4.0f)));

                    b.should(should -> should
                            .match(m -> m
                                    .field("filename.search")
                                    .query(context.originalQuery())
                                    .boost(3.0f)));

                    // Multiple case variations for exact filename matches
                    b.should(should -> should
                            .bool(exactMatch -> {
                                // Original case
                                exactMatch.should(s -> s
                                        .term(t -> t
                                                .field("filename.raw")
                                                .value(context.originalQuery())
                                                .boost(5.0f)));

                                // Lowercase variation
                                exactMatch.should(s -> s
                                        .term(t -> t
                                                .field("filename.raw")
                                                .value(context.lowercaseQuery())
                                                .boost(4.5f)));

                                // Uppercase variation
                                exactMatch.should(s -> s
                                        .term(t -> t
                                                .field("filename.raw")
                                                .value(context.uppercaseQuery())
                                                .boost(4.5f)));

                                return exactMatch;
                            }));

                    return b.minimumShouldMatch("1");
                }));
    }

    private List<HighlightField> configureHighlightFields(SearchContext context) {
        List<HighlightField> highlightFields = new ArrayList<>();
        HighlightFieldParameters params = HighlightFieldParameters.builder()
                .withPreTags("<em><b>")
                .withPostTags("</b></em>")
                .withFragmentSize(context.queryType() == QueryType.DEFINITION ? 200 : 150)
                .withNumberOfFragments(context.queryType() == QueryType.DEFINITION ? 1 : 2)
                .withType("unified")
                .build();

        highlightFields.add(new HighlightField("filename.analyzed", params));
        highlightFields.add(new HighlightField("filename.search", params));
        highlightFields.add(new HighlightField("content", params));

        return highlightFields;
    }

    private List<HighlightField> configureSuggestionHighlightFields() {
        List<HighlightField> highlightFields = new ArrayList<>();
        // Highlight filename with Vietnamese and basic analyzers
        HighlightFieldParameters filenameParams = HighlightFieldParameters.builder()
                .withPreTags("<em><b>")
                .withPostTags("</b></em>")
                .withFragmentSize(60)
                .withNumberOfFragments(1)
                .withType("unified")
                .build();

        highlightFields.add(new HighlightField("filename.analyzed", filenameParams));
        highlightFields.add(new HighlightField("filename.search", filenameParams));

        // Highlight content with Vietnamese analyzer
        HighlightFieldParameters contentParams = HighlightFieldParameters.builder()
                .withPreTags("<em><b>")
                .withPostTags("</b></em>")
                .withFragmentSize(150)
                .withNumberOfFragments(2)
                .withType("unified")
                .build();

        highlightFields.add(new HighlightField("content", contentParams));

        return highlightFields;
    }

    private Page<DocumentResponseDto> processSearchResults(SearchHits<DocumentIndex> searchHits, Pageable pageable) {
        List<DocumentResponseDto> documentResponses = searchHits.getSearchHits().stream()
                .map(hit -> {
                    DocumentIndex doc = hit.getContent();
                    List<String> highlights = new ArrayList<>();

                    // Collect all highlights
                    Map<String, List<String>> highlightFields = hit.getHighlightFields();
                    // First add filename highlights if they exist
                    addHighlightsFromField(highlightFields, "filename.analyzed", highlights);
                    addHighlightsFromField(highlightFields, "filename.search", highlights);

                    // Then add content highlights
                    addHighlightsFromField(highlightFields, "content", highlights);

                    // Build the response DTO
                    return DocumentResponseDto.builder()
                            .id(doc.getId())
                            .filename(doc.getFilename())
                            .documentType(doc.getDocumentType())
                            .major(doc.getMajor())
                            .courseCode(doc.getCourseCode())
                            .courseLevel(doc.getCourseLevel())
                            .category(doc.getCategory())
                            .tags(doc.getTags())
                            .fileSize(doc.getFileSize())
                            .mimeType(doc.getMimeType())
                            .userId(doc.getUserId())
                            .createdAt(doc.getCreatedAt())
                            .highlights(highlights)
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(
                documentResponses,
                pageable,
                searchHits.getTotalHits()
        );
    }

    private List<String> processSuggestionResults(SearchHits<DocumentIndex> searchHits) {
        return searchHits.getSearchHits().stream()
                .map(hit -> {
                    Map<String, List<String>> highlightFields = hit.getHighlightFields();

                    // First try to get filename highlights
                    List<String> filenameHighlightsAnalyzed = highlightFields.get("filename.analyzed");
                    if (filenameHighlightsAnalyzed != null && !filenameHighlightsAnalyzed.isEmpty()) {
                        return filenameHighlightsAnalyzed.get(0);
                    }

                    List<String> filenameHighlightsSearch = highlightFields.get("filename.search");
                    if (filenameHighlightsSearch != null && !filenameHighlightsSearch.isEmpty()) {
                        return filenameHighlightsSearch.get(0);
                    }

                    // Then try content highlights
                    List<String> contentHighlights = highlightFields.get("content");
                    if (contentHighlights != null && !contentHighlights.isEmpty()) {
                        return contentHighlights.get(0);
                    }

                    // Fallback to original filename
                    return hit.getContent().getFilename();
                })
                .distinct()
                .collect(Collectors.toList());
    }

    private void addHighlightsFromField(Map<String, List<String>> highlightFields, String fieldName, List<String> highlights) {
        List<String> fieldHighlights = highlightFields.get(fieldName);
        if (fieldHighlights != null && !fieldHighlights.isEmpty()) {
            highlights.addAll(fieldHighlights);
        }
    }

    private void addSharingAccessFilter(BoolQuery.Builder queryBuilder, String userId) {
        // Exclude deleted documents
        queryBuilder.filter(f -> f
                .term(t -> t
                        .field("deleted")
                        .value(false)));

        // Add sharing access filters
        queryBuilder.filter(f -> f
                .bool(b -> {
                    return b.should(s -> s
                                    // Owner access
                                    .term(t -> t
                                            .field("user_id")
                                            .value(userId)))
                            .should(s -> s
                                    // Public access
                                    .term(t -> t
                                            .field("sharing_type")
                                            .value(SharingType.PUBLIC.name())))
                            .should(s -> s
                                    // Specific users access
                                    .bool(sb -> sb
                                            .must(m -> m
                                                    .term(t -> t
                                                            .field("sharing_type")
                                                            .value(SharingType.SPECIFIC.name())))
                                            .must(m -> m
                                                    .terms(t -> t
                                                            .field("shared_with")
                                                            .terms(tt -> tt
                                                                    .value(Collections.singletonList(FieldValue.of(userId))))))))
                            .minimumShouldMatch("1");
                }));
    }
}