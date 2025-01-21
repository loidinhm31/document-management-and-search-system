package com.dms.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.dms.search.client.UserClient;
import com.dms.search.dto.DocumentResponseDto;
import com.dms.search.dto.SearchContext;
import com.dms.search.dto.UserDto;
import com.dms.search.elasticsearch.DocumentIndex;
import com.dms.search.enums.QueryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSearchService {
    private final ElasticsearchTemplate elasticsearchTemplate;
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
                                .field("userId")
                                .value(userId)));

        // Build query based on context
        if (Objects.requireNonNull(context.queryType()) == QueryType.DEFINITION) {
            queryBuilder.must(must -> must
                    .bool(b -> {
                        // Higher boost for phrase matches
                        b.should(should -> should
                                .matchPhrase(mp -> mp
                                        .field("content")
                                        .query(context.originalQuery())
                                        .boost(15.0f)));

                        // Cross-field matching for better relevance
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
                        // Case-insensitive text search
                        b.should(should -> should
                                .match(m -> m
                                        .field("content")
                                        .query(context.originalQuery())
                                        .fuzziness("AUTO")
                                        .operator(Operator.Or)
                                        .boost(1.0f)));

                        // Try both original case and lowercase for exact matches
                        b.should(should -> should
                                .term(t -> t
                                        .field("content")
                                        .value(context.originalQuery())
                                        .boost(2.0f)));

                        b.should(should -> should
                                .term(t -> t
                                        .field("content")
                                        .value(context.lowercaseQuery())
                                        .boost(1.5f)));

                        // Phrase matching with slop
                        b.should(should -> should
                                .matchPhrase(mp -> mp
                                        .field("content")
                                        .query(context.originalQuery())
                                        .slop(2)
                                        .boost(2.0f)));

                        // Filename matching with case variations
                        b.should(should -> should
                                .match(m -> m
                                        .field("filename")
                                        .query(context.originalQuery())
                                        .boost(3.0f)));

                        b.should(should -> should
                                .term(t -> t
                                        .field("filename.raw")
                                        .value(context.originalQuery())
                                        .boost(4.0f)));

                        // Cross-field matching for better relevance
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

        return queryBuilder.build()._toQuery();
    }

    private Query buildSuggestionQuery(String prefix, String userId) {
        return new BoolQuery.Builder()
                .must(must -> must
                        .term(term -> term
                                .field("userId")
                                .value(userId)))
                .must(must -> must
                        .bool(b -> {
                            // Match in filename
                            b.should(should -> should
                                    .match(m -> m
                                            .field("filename")
                                            .query(prefix)
                                            .boost(2.0f)));

                            // Match in content
                            b.should(should -> should
                                    .match(m -> m
                                            .field("content")
                                            .query(prefix)
                                            .boost(1.0f)));

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

            Query query = buildSearchQuery(searchContext, userDto.getUserId().toString());
            float minScore = getMinScore(searchQuery, searchContext);

            HighlightFieldParameters contentParams = HighlightFieldParameters.builder()
                    .withPreTags("<em><b>")
                    .withPostTags("</b></em>")
                    .withFragmentSize(searchContext.queryType() == QueryType.DEFINITION ? 200 : 150)
                    .withNumberOfFragments(searchContext.queryType() == QueryType.DEFINITION ? 1 : 2)
                    .build();

            HighlightField contentHighlight = new HighlightField("content", contentParams);
            Highlight highlight = new Highlight(List.of(contentHighlight));

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withHighlightQuery(new HighlightQuery(highlight, DocumentIndex.class))
                    .withPageable(pageable)
                    .withTrackScores(true)
                    .build();

            SearchHits<DocumentIndex> searchHits = elasticsearchTemplate.search(
                    nativeQuery,
                    DocumentIndex.class
            );

            List<DocumentResponseDto> documents = searchHits.getSearchHits().stream()
                    .filter(hit -> hit.getScore() >= minScore)
                    .map(hit -> {
                        DocumentIndex doc = hit.getContent();
                        // Extract highlights from the hit
                        List<String> highlights = hit.getHighlightFields().get("content");

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
                                .highlights(highlights != null ? highlights : new ArrayList<>())
                                .build();
                    })
                    .collect(Collectors.toList());

            return new PageImpl<>(
                    documents,
                    pageable,
                    searchHits.getTotalHits()
            );

        } catch (Exception e) {
            log.error("Search operation failed. Query: {}, Error: {}", searchQuery, e.getMessage());
            throw new RuntimeException("Failed to perform document search", e);
        }
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

            // Configure highlighting for both filename and content
            HighlightFieldParameters highlightParams = HighlightFieldParameters.builder()
                    .withPreTags("<em><b>")
                    .withPostTags("</b></em>")
                    .withFragmentSize(60)
                    .withNumberOfFragments(3)
                    .build();

            List<HighlightField> highlightFields = new ArrayList<>();
            highlightFields.add(new HighlightField("filename", highlightParams));
            highlightFields.add(new HighlightField("content", highlightParams));

            Highlight highlight = new Highlight(highlightFields);

            Query query = buildSuggestionQuery(prefix, userDto.getUserId().toString());

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withHighlightQuery(new HighlightQuery(highlight, DocumentIndex.class))
                    .withPageable(PageRequest.of(0, MAX_SUGGESTIONS))
                    .withTrackScores(true)
                    .build();

            SearchHits<DocumentIndex> searchHits = elasticsearchTemplate.search(
                    nativeQuery,
                    DocumentIndex.class
            );

            return searchHits.getSearchHits().stream()
                    .map(hit -> {
                        // Get highlighted fragments
                        List<String> fileHighlights = hit.getHighlightFields().get("filename");
                        List<String> contentHighlights = hit.getHighlightFields().get("content");

                        // Prioritize filename matches, fallback to content matches
                        if (fileHighlights != null && !fileHighlights.isEmpty()) {
                            return fileHighlights.get(0);
                        } else if (contentHighlights != null && !contentHighlights.isEmpty()) {
                            return contentHighlights.get(0);
                        }

                        // Fallback to original filename if no highlights
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