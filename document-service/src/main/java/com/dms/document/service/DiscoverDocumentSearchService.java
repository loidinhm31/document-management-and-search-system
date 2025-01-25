package com.dms.document.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.dms.document.client.UserClient;
import com.dms.document.dto.DocumentResponseDto;
import com.dms.document.dto.SearchContext;
import com.dms.document.dto.UserDto;
import com.dms.document.elasticsearch.DocumentIndex;
import com.dms.document.enums.QueryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscoverDocumentSearchService {
    private final ElasticsearchOperations elasticsearchOperations;
    private final UserClient userClient;

    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SUGGESTIONS = 20;

    private float getMinScore(String query, SearchContext context) {
        int length = query.trim().length();

        // Lowered base scores to accommodate case-insensitive matching
        float baseScore;
        if (context.queryType() == QueryType.DEFINITION) {
            baseScore = 8.0f;
        } else {
            baseScore = 15.0f;
        }

        if (length <= 3) return baseScore * 0.5f;
        if (length <= 5) return baseScore * 0.65f;
        if (length <= 10) return baseScore * 0.85f;

        return baseScore;
    }

    private SearchContext analyzeQuery(String query) {
        String cleanQuery = query.trim();
        boolean isProbableDefinition = cleanQuery.toLowerCase().split("\\s+").length <= 3;

        return new SearchContext(
                isProbableDefinition ? QueryType.DEFINITION : QueryType.GENERAL,
                cleanQuery,
                cleanQuery.toUpperCase(),
                cleanQuery.toLowerCase()
        );
    }

    private Query buildSearchQuery(SearchContext context, String userId) {
        BoolQuery.Builder queryBuilder = new BoolQuery.Builder()
                .must(must -> must
                        .term(term -> term
                                .field("user_id")
                                .value(userId)));

        // Build query based on context
        if (Objects.requireNonNull(context.queryType()) == QueryType.DEFINITION) {
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
        } else {
            queryBuilder.must(must -> must
                    .bool(b -> {
                        // Vietnamese analyzed content search
                        b.should(should -> should
                                .match(m -> m
                                        .field("content")
                                        .analyzer("vietnamese_analyzer")
                                        .query(context.originalQuery())
                                        .boost(2.0f)));

                        // Fuzzy search on base content
                        b.should(should -> should
                                .match(m -> m
                                        .field("content")
                                        .query(context.originalQuery())
                                        .fuzziness("AUTO")
                                        .operator(Operator.Or)
                                        .boost(1.0f)));

                        // Exact matches on content
                        b.should(should -> should
                                .term(t -> t
                                        .field("content.keyword")
                                        .value(context.originalQuery())
                                        .boost(3.0f)));

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

                        // Exact filename match
                        b.should(should -> should
                                .term(t -> t
                                        .field("filename.raw")
                                        .value(context.originalQuery())
                                        .boost(5.0f)));

                        return b.minimumShouldMatch("1");
                    }));
        }

        return queryBuilder.build()._toQuery();
    }

    private Query buildSuggestionQuery(String prefix, String userId) {
        return new BoolQuery.Builder()
                .must(must -> must
                        .term(term -> term
                                .field("user_id")
                                .value(userId)))
                .must(must -> must
                        .bool(b -> {
                            // Vietnamese-aware filename match
                            b.should(should -> should
                                    .match(m -> m
                                            .field("filename.analyzed")
                                            .query(prefix)
                                            .boost(4.0f)));

                            // Basic filename search
                            b.should(should -> should
                                    .match(m -> m
                                            .field("filename.search")
                                            .query(prefix)
                                            .boost(3.0f)));

                            // Exact filename match
                            b.should(should -> should
                                    .term(t -> t
                                            .field("filename.raw")
                                            .value(prefix)
                                            .boost(5.0f)));

                            // Content search with Vietnamese analysis
                            b.should(should -> should
                                    .match(m -> m
                                            .field("content")
                                            .analyzer("vietnamese_analyzer")
                                            .query(prefix)
                                            .boost(2.0f)));

                            return b.minimumShouldMatch("1");
                        }))
                .build()._toQuery();
    }

    public Page<DocumentResponseDto> searchDocuments(String searchQuery, String username, Pageable pageable) {
        try {
            ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
            if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
                throw new InvalidDataAccessResourceUsageException("User not found");
            }

            if (searchQuery.trim().length() < MIN_SEARCH_LENGTH) {
                return Page.empty(pageable);
            }

            UserDto userDto = response.getBody();
            SearchContext searchContext = analyzeQuery(searchQuery);
            float minScore = getMinScore(searchQuery, searchContext);

            Query query = buildSearchQuery(searchContext, userDto.getUserId().toString());

            // Configure highlighting
            List<HighlightField> highlightFields = new ArrayList<>();

            // Configure separate highlight parameters for each field
            HighlightFieldParameters filenameParams = HighlightFieldParameters.builder()
                    .withPreTags("<em><b>")
                    .withPostTags("</b></em>")
                    .withFragmentSize(searchContext.queryType() == QueryType.DEFINITION ? 200 : 150)
                    .withNumberOfFragments(searchContext.queryType() == QueryType.DEFINITION ? 1 : 2)
                    .withType("unified")
                    .build();

            highlightFields.add(new HighlightField("filename.analyzed", filenameParams));
            highlightFields.add(new HighlightField("filename.search", filenameParams));

            HighlightFieldParameters contentParams = HighlightFieldParameters.builder()
                    .withPreTags("<em><b>")
                    .withPostTags("</b></em>")
                    .withFragmentSize(searchContext.queryType() == QueryType.DEFINITION ? 200 : 150)
                    .withNumberOfFragments(searchContext.queryType() == QueryType.DEFINITION ? 1 : 2)
                    .withType("unified")
                    .build();

            highlightFields.add(new HighlightField("content", contentParams));

            Highlight highlight = new Highlight(highlightFields);

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withHighlightQuery(new HighlightQuery(highlight, DocumentIndex.class))
                    .withPageable(pageable)
                    .withTrackScores(true)
                    .withMinScore(minScore)
                    .build();

            SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                    nativeQuery,
                    DocumentIndex.class
            );

            return processSearchResults(searchHits, pageable);
        } catch (Exception e) {
            log.error("Search operation failed. Query: {}, Error: {}", searchQuery, e.getMessage());
            throw new RuntimeException("Failed to perform document search", e);
        }
    }

    private Page<DocumentResponseDto> processSearchResults(SearchHits<DocumentIndex> searchHits, Pageable pageable) {
        List<DocumentResponseDto> allDocuments = searchHits.getSearchHits().stream()
                .map(hit -> {
                    DocumentIndex doc = hit.getContent();
                    List<String> highlights = new ArrayList<>();

                    // Collect all highlights
                    Map<String, List<String>> highlightFields = hit.getHighlightFields();
                    highlightFields.values().forEach(highlights::addAll);

                    doc.setContent(null);
                    return DocumentResponseDto.builder()
                            .id(hit.getId())
                            .filename(doc.getFilename())
                            .courseCode(doc.getCourseCode())
                            .documentType(doc.getDocumentType())
                            .major(doc.getMajor())
                            .courseLevel(doc.getCourseLevel())
                            .category(doc.getCategory())
                            .fileSize(doc.getFileSize())
                            .mimeType(doc.getMimeType())
                            .tags(doc.getTags())
                            .createdAt(doc.getCreatedAt())
                            .userId(doc.getUserId())
                            .highlights(highlights)
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(
                allDocuments,
                pageable,
                searchHits.getTotalHits()
        );
    }

    public List<String> getSuggestions(String prefix, String username) {
        try {
            if (prefix.trim().length() < MIN_SEARCH_LENGTH) {
                return List.of();
            }

            ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
            if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
                return List.of();
            }

            UserDto userDto = response.getBody();

            // Configure highlighting
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

            Highlight highlight = new Highlight(highlightFields);

            Query query = buildSuggestionQuery(prefix, userDto.getUserId().toString());

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

            return searchHits.getSearchHits().stream()
                    .map(hit -> {
                        // Prioritize filename highlights
                        List<String> filenameHighlightsAnalyzed = hit.getHighlightFields().get("filename.analyzed");
                        List<String> filenameHighlightsSearch = hit.getHighlightFields().get("filename.search");
                        List<String> contentHighlights = hit.getHighlightFields().get("content");

                        if (filenameHighlightsAnalyzed != null && !filenameHighlightsAnalyzed.isEmpty()) {
                            return filenameHighlightsAnalyzed.get(0);
                        } else if (filenameHighlightsSearch != null && !filenameHighlightsSearch.isEmpty()) {
                            return filenameHighlightsSearch.get(0);
                        } else if (contentHighlights != null && !contentHighlights.isEmpty()) {
                            return contentHighlights.get(0);
                        }

                        // Fallback to original filename
                        return hit.getContent().getFilename();
                    })
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting suggestions: {}", e.getMessage());
            return List.of();
        }
    }

}