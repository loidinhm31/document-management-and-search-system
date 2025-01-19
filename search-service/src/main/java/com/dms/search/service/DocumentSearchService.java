package com.dms.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.dms.search.elasticsearch.DocumentIndex;
import com.dms.search.entity.User;
import com.dms.search.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSearchService {
    private final ElasticsearchTemplate elasticsearchTemplate;
    private final UserRepository userRepository;

    private static final int MIN_SEARCH_LENGTH = 2;

    // Adjust score thresholds based on query length
    private float getMinScore(String query, boolean isDefinitionTerm) {
        if (isDefinitionTerm) {
            return 20.0f; // Lower threshold for definition queries
        }
        int length = query.trim().length();
        if (length <= 3) return 3.0f;  // For very short queries like "AWS
        if (length <= 10) return 10.0f;  // For short queries
        return 30.0f;  // For longer queries
    }

    // Check if the query is likely a technical term
    private boolean containsDefinitionTerm(String query) {
        // Technical terms are often uppercase or contain numbers
        String cleanQuery = query.trim();
        return cleanQuery.matches(".*[A-Z0-9]+.*") ||
                cleanQuery.equals(cleanQuery.toUpperCase());
    }

    public Page<DocumentIndex> searchDocuments(String searchQuery, String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidDataAccessResourceUsageException("User not found"));

        try {
            // Clean and prepare the search query
            String cleanQuery = searchQuery.trim();
            boolean isDefinitionTerm = containsDefinitionTerm(cleanQuery);

            float minScore = getMinScore(cleanQuery, isDefinitionTerm);

            if (!StringUtils.hasText(cleanQuery) || cleanQuery.length() < MIN_SEARCH_LENGTH) {
                return Page.empty(pageable);
            }

            // Split query into terms for better matching
            String[] terms = cleanQuery.split("\\s+");

            BoolQuery.Builder queryBuilder = new BoolQuery.Builder()
                    .must(must -> must
                            .term(term -> term
                                    .field("userId")
                                    .value(user.getUserId().toString())));

            // Add should clauses for the full phrase and individual terms
            queryBuilder.must(must -> must
                    .bool(b -> {
                        // Full phrase match with high boost
                        b.should(should -> should
                                .matchPhrase(mp -> mp
                                        .field("content")
                                        .query(cleanQuery)
                                        .boost(10.0f)));

                        // Individual term matches with varying boosts
                        for (String term : terms) {
                            boolean isTermTechnical = containsDefinitionTerm(term);

                            // Exact term match
                            b.should(should -> should
                                    .term(t -> t
                                            .field("content")
                                            .value(term)
                                            .boost(isTermTechnical ? 8.0f : 3.0f)));

                            // Fuzzy match for non-technical terms
                            if (!isTermTechnical) {
                                b.should(should -> should
                                        .fuzzy(f -> f
                                                .field("content")
                                                .value(term)
                                                .boost(1.0f)
                                                .fuzziness("1")));
                            }
                        }

                        // Cross-field matching for better relevance
                        b.should(should -> should
                                .multiMatch(mm -> mm
                                        .query(cleanQuery)
                                        .fields("filename^4", "content^2")
                                        .type(TextQueryType.CrossFields)
                                        .operator(Operator.And)
                                        .minimumShouldMatch("75%")
                                        .boost(4.0f)));

                        // Adapt minimumShouldMatch based on query
                        String minMatch = (terms.length == 1 && terms[0].length() <= 5) ? "1" : "2";
                        return b.minimumShouldMatch(minMatch);
                    }));


            // Optionally, set highlight parameters like pre- and post-tags, fragment size, etc.
            HighlightFieldParameters filenameParams = HighlightFieldParameters.builder()
                    .withPreTags("<em>")
                    .withPostTags("</em>")
                    .withFragmentSize(100)
                    .withNumberOfFragments(3)
                    .build();

            HighlightFieldParameters contentParams = HighlightFieldParameters.builder()
                    .withPreTags("<mark>")
                    .withPostTags("</mark>")
                    .withFragmentSize(150)
                    .withNumberOfFragments(2)
                    .build();

            // Create highlight fields for the "filename" and "content" fields
            HighlightField filenameHighlight = new HighlightField("filename", filenameParams);
            HighlightField contentHighlight = new HighlightField("content", contentParams);

            // Create a Highlight object with both fields and default parameters
            Highlight highlight = new Highlight(List.of(filenameHighlight, contentHighlight));

            HighlightQuery highlightQuery = new HighlightQuery(highlight, DocumentIndex.class);

            // Create native query
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(queryBuilder.build()._toQuery())
                    .withPageable(pageable)
                    .withTrackScores(true)
                    .withHighlightQuery(highlightQuery)
                    .build();

            // Execute search
            SearchHits<DocumentIndex> searchHits = elasticsearchTemplate.search(
                    nativeQuery,
                    DocumentIndex.class
            );

            // Filter and process results
            List<DocumentIndex> documents = searchHits.getSearchHits().stream()
                    .filter(hit -> hit.getScore() >= minScore)
                    .map(SearchHit::getContent)
                    .peek(a -> a.setContent(null))
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

            Query boolQuery = new BoolQuery.Builder()
                    .must(must -> must
                            .term(term -> term
                                    .field("userId")
                                    .value(userId)))
                    .must(must -> must
                            .bool(b -> b
                                    .should(should -> should
                                            .prefix(p -> p
                                                    .field("filename")
                                                    .value(prefix)))
                                    .should(should -> should
                                            .prefix(p -> p
                                                    .field("filename")
                                                    .value(prefix)))
                                    .minimumShouldMatch("1")))
                    .build()._toQuery();

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(boolQuery)
                    .withPageable(PageRequest.of(0, 5, Sort.by("filename.raw").ascending()))
                    .build();

            SearchHits<DocumentIndex> searchHits = elasticsearchTemplate.search(
                    nativeQuery,
                    DocumentIndex.class
            );

            return searchHits.getSearchHits().stream()
                    .map(hit -> hit.getContent().getFilename())
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting suggestions", e);
            return List.of();
        }
    }
}