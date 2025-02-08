package com.dms.document.search.service;


import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.UserDto;
import com.dms.document.search.elasticsearch.DocumentIndex;
import com.dms.document.search.exception.InvalidDocumentException;
import com.dms.document.search.model.DocumentPreferences;
import com.dms.document.search.repository.DocumentPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRecommendationService extends ElasticSearchBaseService {
    private final UserClient userClient;
    private final ElasticsearchOperations elasticsearchOperations;
    private final DocumentPreferencesRepository documentPreferencesRepository;

    private static final float MAX_INTERACTION_BOOST = 3.0f;
    private static final float INTERACTION_WEIGHT_MULTIPLIER = 0.5f;
    private static final float PREFERENCE_BOOST_MULTIPLIER = 2.0f;

    public Page<DocumentResponseDto> getRecommendations(
            String documentId,
            String username,
            Pageable pageable) {

        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }

        UserDto userDto = response.getBody();
        DocumentPreferences preferences = getOrCreatePreferences(userDto.getUserId().toString());

        return StringUtils.isNotEmpty(documentId)
                ? getContentBasedRecommendations(documentId, userDto.getUserId().toString(), preferences, pageable)
                : getPreferenceBasedRecommendations(userDto.getUserId().toString(), preferences, pageable);
    }

    private DocumentPreferences getOrCreatePreferences(String userId) {
        return documentPreferencesRepository.findByUserId(userId)
                .orElseGet(() -> {
                    DocumentPreferences defaultPrefs = new DocumentPreferences();
                    defaultPrefs.setUserId(userId);
                    defaultPrefs.setCreatedAt(new Date());
                    defaultPrefs.setUpdatedAt(new Date());
                    return documentPreferencesRepository.save(defaultPrefs);
                });
    }

    private void addPreferenceBoosts(BoolQuery.Builder queryBuilder, DocumentPreferences preferences) {
        if (preferences == null) return;

        // Add preferred majors boost
        addPreferredFieldBoost(queryBuilder, "major", preferences.getPreferredMajors(), 3.0f);

        // Add preferred levels boost
        addPreferredFieldBoost(queryBuilder, "course_level", preferences.getPreferredLevels(), 2.0f);

        // Add preferred categories boost
        addPreferredFieldBoost(queryBuilder, "category", preferences.getPreferredCategories(), 2.5f);

        // Add preferred tags boost
        addPreferredFieldBoost(queryBuilder, "tags", preferences.getPreferredTags(), 2.0f);

        // Add content type weights
        addContentTypeWeights(queryBuilder, preferences.getContentTypeWeights());

        // Add interaction history boosts
        addInteractionHistoryBoosts(queryBuilder, preferences);
    }

    private void addPreferredFieldBoost(BoolQuery.Builder queryBuilder, String field, Set<String> values, float boost) {
        if (CollectionUtils.isNotEmpty(values)) {
            queryBuilder.should(s -> s
                    .terms(t -> t
                            .field(field)
                            .terms(tt -> tt.value(values.stream()
                                    .map(FieldValue::of)
                                    .collect(Collectors.toList())))
                            .boost(boost * PREFERENCE_BOOST_MULTIPLIER)
                    )
            );
        }
    }

    private void addContentTypeWeights(BoolQuery.Builder queryBuilder, Map<String, Double> contentTypeWeights) {
        if (contentTypeWeights != null && !contentTypeWeights.isEmpty()) {
            contentTypeWeights.forEach((type, weight) -> {
                if (weight > 0) {
                    queryBuilder.should(s -> s
                            .bool(bb -> bb
                                    .must(m -> m.term(t -> t
                                            .field("document_type")
                                            .value(type)))
                                    .boost(weight.floatValue() * PREFERENCE_BOOST_MULTIPLIER)
                            )
                    );
                }
            });
        }
    }

    private void addInteractionHistoryBoosts(BoolQuery.Builder queryBuilder, DocumentPreferences preferences) {
        addInteractionCountBoosts(queryBuilder, "category", preferences.getCategoryInteractionCounts());
        addInteractionCountBoosts(queryBuilder, "major", preferences.getMajorInteractionCounts());
        addInteractionCountBoosts(queryBuilder, "course_level", preferences.getLevelInteractionCounts());
        addInteractionCountBoosts(queryBuilder, "tags", preferences.getTagInteractionCounts());
    }

    private void addInteractionCountBoosts(BoolQuery.Builder queryBuilder, String field, Map<String, Integer> interactionCounts) {
        if (interactionCounts != null) {
            interactionCounts.forEach((value, count) -> {
                if (count > 0) {
                    float boost = Math.min(count * INTERACTION_WEIGHT_MULTIPLIER, MAX_INTERACTION_BOOST);
                    queryBuilder.should(s -> s
                            .term(t -> t
                                    .field(field)
                                    .value(value)
                                    .boost(boost)
                            )
                    );
                }
            });
        }
    }

    private void addLanguagePreferences(BoolQuery.Builder queryBuilder, Set<String> languagePreferences) {
        if (CollectionUtils.isNotEmpty(languagePreferences)) {
            queryBuilder.should(s -> s
                    .terms(t -> t
                            .field("language")
                            .terms(tt -> tt.value(languagePreferences.stream()
                                    .map(FieldValue::of)
                                    .collect(Collectors.toList())))
                            .boost(5.0f)
                    )
            );
        }
    }

    private void addRecentViewsBoost(BoolQuery.Builder queryBuilder, Set<String> recentViewedDocuments) {
        if (CollectionUtils.isNotEmpty(recentViewedDocuments)) {
            queryBuilder.should(s -> s
                    .terms(t -> t
                            .field("_id")
                            .terms(tt -> tt.value(recentViewedDocuments.stream()
                                    .map(FieldValue::of)
                                    .collect(Collectors.toList())))
                            .boost(1.5f)
                    )
            );
        }
    }

    public Page<DocumentResponseDto> getContentBasedRecommendations(
            String documentId,
            String userId,
            DocumentPreferences preferences,
            Pageable pageable) {

        DocumentIndex sourceDoc = elasticsearchOperations.get(documentId, DocumentIndex.class);
        if (sourceDoc == null) {
            throw new InvalidDocumentException("Document not found");
        }

        BoolQuery.Builder queryBuilder = new BoolQuery.Builder();
        addSharingAccessFilter(queryBuilder, userId);

        // Exclude source document
        queryBuilder.mustNot(mustNot -> mustNot
                .term(t -> t
                        .field("_id")
                        .value(documentId)));

        queryBuilder.must(must -> must
                .bool(b -> {
                    // Content similarity
                    addContentSimilarityBoosts(b, sourceDoc);

                    // Metadata similarity
                    addMetadataSimilarityBoosts(b, sourceDoc);

                    // Add preference-based boosts
                    addPreferenceBoosts(queryBuilder, preferences);

                    // Add language preferences
                    addLanguagePreferences(queryBuilder, preferences.getLanguagePreferences());

                    // Add recent views boost
                    addRecentViewsBoost(queryBuilder, preferences.getRecentViewedDocuments());

                    return b.minimumShouldMatch("1");
                }));

        return executeSearch(queryBuilder, pageable);
    }

    private void addContentSimilarityBoosts(BoolQuery.Builder queryBuilder, DocumentIndex sourceDoc) {
        // Content similarity using more-like-this
        queryBuilder.should(should -> should
                .moreLikeThis(mlt -> mlt
                        .fields("content")
                        .like(l -> l.text(sourceDoc.getContent()))
                        .minTermFreq(2)
                        .minDocFreq(1)
                        .maxQueryTerms(25)
                        .minimumShouldMatch("30%")
                        .boost(10.0f)));

        // Title similarity
        queryBuilder.should(should -> should
                .moreLikeThis(mlt -> mlt
                        .fields("filename.analyzed")
                        .like(l -> l.text(sourceDoc.getFilename()))
                        .minTermFreq(1)
                        .minDocFreq(1)
                        .maxQueryTerms(10)
                        .boost(5.0f)));
    }

    private void addMetadataSimilarityBoosts(BoolQuery.Builder queryBuilder, DocumentIndex sourceDoc) {
        // Same major and level
        if (sourceDoc.getMajor() != null && sourceDoc.getCourseLevel() != null) {
            queryBuilder.should(should -> should
                    .bool(bb -> bb
                            .must(m -> m.term(t -> t
                                    .field("major")
                                    .value(sourceDoc.getMajor())))
                            .must(m -> m.term(t -> t
                                    .field("course_level")
                                    .value(sourceDoc.getCourseLevel())))
                            .boost(3.0f)));
        }

        // Same category
        if (sourceDoc.getCategory() != null) {
            queryBuilder.should(should -> should
                    .term(t -> t
                            .field("category")
                            .value(sourceDoc.getCategory())
                            .boost(2.0f)));
        }

        // Course code similarity
        addCourseCodeSimilarity(queryBuilder, sourceDoc.getCourseCode());

        // Common tags
        if (!CollectionUtils.isEmpty(sourceDoc.getTags())) {
            queryBuilder.should(should -> should
                    .terms(t -> t
                            .field("tags")
                            .terms(tt -> tt.value(sourceDoc.getTags().stream()
                                    .map(FieldValue::of)
                                    .collect(Collectors.toList())))
                            .boost(4.0f)));
        }
    }

    private void addCourseCodeSimilarity(BoolQuery.Builder queryBuilder, String courseCode) {
        if (courseCode != null) {
            queryBuilder.should(should -> should
                    .bool(bb -> {
                        // Exact match
                        bb.should(s -> s
                                .term(t -> t
                                        .field("course_code")
                                        .value(courseCode)
                                        .boost(8.0f)));

                        // Fuzzy match
                        bb.should(s -> s
                                .match(m -> m
                                        .field("course_code")
                                        .query(courseCode)
                                        .fuzziness("2")
                                        .prefixLength(3)
                                        .boost(6.0f)));

                        return bb;
                    }));
        }
    }

    private Page<DocumentResponseDto> executeSearch(BoolQuery.Builder queryBuilder, Pageable pageable) {
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(queryBuilder.build()._toQuery())
                .withPageable(PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Order.desc("_score")) // Sort by score in descending order
                ))
                .withTrackScores(true)
                .build();

        SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                searchQuery,
                DocumentIndex.class
        );

        return processSearchResults(searchHits, pageable);
    }

    private Page<DocumentResponseDto> getPreferenceBasedRecommendations(
            String userId,
            DocumentPreferences preferences,
            Pageable pageable) {

        NativeQueryBuilder queryBuilder = NativeQuery.builder();

        queryBuilder.withQuery(query -> query
                .bool(b -> {
                    // Add base access control filters
                    addSharingAccessFilter(b, userId);

                    // Add preference-based boosts
                    addPreferenceBoosts(b, preferences);

                    // Add language preferences
                    addLanguagePreferences(b, preferences.getLanguagePreferences());

                    // Add recent views boost
                    addRecentViewsBoost(b, preferences.getRecentViewedDocuments());

                    return b;
                }));

        queryBuilder.withPageable(PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("_score")) // Sort by score in descending order
        ));

        SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                queryBuilder.build(),
                DocumentIndex.class
        );

        return processSearchResults(searchHits, pageable);
    }
}