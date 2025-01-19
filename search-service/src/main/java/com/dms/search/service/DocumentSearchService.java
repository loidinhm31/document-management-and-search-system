package com.dms.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
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
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSearchService {
    private final ElasticsearchTemplate elasticsearchTemplate;
    private final UserRepository userRepository;

    private static final float MIN_SCORE = 30f;
    private static final int MIN_SEARCH_LENGTH = 2;

    private String normalizeText(String text) {
        text = text.replaceAll("đ", "d").replaceAll("Đ", "D");

        text = text.replaceAll("æ", "ae").replaceAll("œ", "oe");

        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
    }

    public Page<DocumentIndex> searchDocuments(String searchQuery, String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidDataAccessResourceUsageException("User not found"));

        try {
            // Clean and prepare the search query
            String cleanQuery = searchQuery.trim();
            String normalizedQuery = normalizeText(cleanQuery);

            if (!StringUtils.hasText(cleanQuery) || cleanQuery.length() < MIN_SEARCH_LENGTH) {
                return Page.empty(pageable);
            }

            // Build the bool query
            Query boolQuery = new BoolQuery.Builder()
                    .must(must -> must
                            .term(term -> term
                                    .field("userId")
                                    .value(user.getUserId().toString())))
                    .must(must -> must
                            .bool(b -> b
                                    // Original text search
                                    .should(should -> should
                                            .match(m -> m
                                                    .field("filename")
                                                    .query(cleanQuery)
                                                    .boost(4.0f)))
                                    .should(should -> should
                                            .match(m -> m
                                                    .field("content")
                                                    .query(cleanQuery)
                                                    .boost(2.0f)))
                                    // Original phrase match
                                    .should(should -> should
                                            .matchPhrase(mp -> mp
                                                    .field("filename")
                                                    .query(cleanQuery)
                                                    .boost(8.0f)))
                                    .should(should -> should
                                            .matchPhrase(mp -> mp
                                                    .field("content")
                                                    .query(cleanQuery)
                                                    .boost(6.0f)))
                                    // Normalized text search with fuzzy support
                                    .should(should -> should
                                            .match(m -> m
                                                    .field("filename")
                                                    .query(normalizedQuery)
                                                    .fuzziness("1") // Allow up to 1 character difference
                                                    .boost(3.0f)))
                                    .should(should -> should
                                            .match(m -> m
                                                    .field("content")
                                                    .query(normalizedQuery)
                                                    .fuzziness("2") // Allow up to 2 character differences
                                                    .boost(1.5f)))
                                    // Multi-match for better cross-field relevance
                                    .should(should -> should
                                            .multiMatch(mm -> mm
                                                    .query(cleanQuery)
                                                    .fields("filename^4", "content^2")
                                                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.CrossFields)
                                                    .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                                                    .boost(5.0f)))
                                    // Require at least one of the should clauses to match
                                    .minimumShouldMatch("1")))
                    .build()._toQuery();




            // Optionally, set highlight parameters like pre and post tags, fragment size, etc.
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
                    .withQuery(boolQuery)
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
                    .filter(hit -> hit.getScore() >= MIN_SCORE)
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
            String normalizedPrefix = normalizeText(prefix);

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
                                                    .value(normalizedPrefix)))
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