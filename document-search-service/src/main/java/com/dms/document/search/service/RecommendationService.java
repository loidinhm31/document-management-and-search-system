package com.dms.document.search.service;


import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.UserDto;
import com.dms.document.search.elasticsearch.DocumentIndex;
import com.dms.document.search.enums.DocumentStatus;
import com.dms.document.search.enums.SharingType;
import com.dms.document.search.exception.InvalidDocumentException;
import com.dms.document.search.model.DocumentPreferences;
import com.dms.document.search.repository.DocumentPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService extends ElasticSearchBaseService {
    private final UserClient userClient;
    private final ElasticsearchOperations elasticsearchOperations;
    private final DocumentPreferencesRepository documentPreferencesRepository;

    public Page<DocumentResponseDto> getRecommendations(
            String documentId,
            String username,
            Pageable pageable) {

        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }

        UserDto userDto = response.getBody();

        DocumentPreferences preferences = documentPreferencesRepository.findByUserId(userDto.getUserId().toString())
                .orElseGet(() -> {
                    DocumentPreferences defaultPrefs = new DocumentPreferences();
                    defaultPrefs.setUserId(userDto.getUserId().toString());

                    // Set timestamps
                    defaultPrefs.setCreatedAt(new Date());
                    defaultPrefs.setUpdatedAt(new Date());

                    return documentPreferencesRepository.save(defaultPrefs);
                });

        if (StringUtils.isNotEmpty(documentId)) {
            return getContentBasedRecommendations(documentId, userDto.getUserId().toString(), preferences, pageable);
        } else {
            return getPreferenceBasedRecommendations(userDto.getUserId().toString(), preferences, pageable);
        }
    }

    public Page<DocumentResponseDto> getContentBasedRecommendations(
            String documentId,
            String userId,
            DocumentPreferences preferences,
            Pageable pageable) {
        // Get the source document
        DocumentIndex sourceDoc = elasticsearchOperations.get(documentId, DocumentIndex.class);
        if (sourceDoc == null) {
            throw new InvalidDocumentException("Document not found");
        }

        // Build the query for related documents
        BoolQuery.Builder queryBuilder = new BoolQuery.Builder();

        // Add sharing access filter
        addSharingAccessFilter(queryBuilder, userId);

        // Exclude the source document
        queryBuilder.mustNot(mustNot -> mustNot
                .term(t -> t
                        .field("_id")
                        .value(documentId)));

        // Build should clauses for different similarity criteria
        queryBuilder.must(must -> must
                .bool(b -> {
                    // Content similarity using more-like-this query
                    b.should(should -> should
                            .moreLikeThis(mlt -> mlt
                                    .fields("content")
                                    .like(l -> l.text(sourceDoc.getContent()))
                                    .minTermFreq(2)
                                    .minDocFreq(1)
                                    .maxQueryTerms(25)
                                    .minimumShouldMatch("30%")
                                    .boost(10.0f)));

                    // Title similarity
                    b.should(should -> should
                            .moreLikeThis(mlt -> mlt
                                    .fields("filename.analyzed")
                                    .like(l -> l.text(sourceDoc.getFilename()))
                                    .minTermFreq(1)
                                    .minDocFreq(1)
                                    .maxQueryTerms(10)
                                    .boost(5.0f)));

                    // Same major and level
                    if (sourceDoc.getMajor() != null && sourceDoc.getCourseLevel() != null) {
                        b.should(should -> should
                                .bool(bb -> bb
                                        .must(m -> m
                                                .term(t -> t
                                                        .field("major")
                                                        .value(sourceDoc.getMajor())))
                                        .must(m -> m
                                                .term(t -> t
                                                        .field("course_level")
                                                        .value(sourceDoc.getCourseLevel())))
                                        .boost(3.0f)));
                    }

                    // Same category
                    if (sourceDoc.getCategory() != null) {
                        b.should(should -> should
                                .term(t -> t
                                        .field("category")
                                        .value(sourceDoc.getCategory())
                                        .boost(2.0f)));
                    }

                    // Course code similarity using fuzzy matching
                    if (sourceDoc.getCourseCode() != null) {
                        String courseCode = sourceDoc.getCourseCode();
                        b.should(should -> should
                                .bool(bb -> {
                                    // Exact match gets highest boost
                                    bb.should(s -> s
                                            .term(t -> t
                                                    .field("course_code")
                                                    .value(courseCode)
                                                    .boost(8.0f)));

                                    // Fuzzy match for similar course codes
                                    bb.should(s -> s
                                            .match(m -> m
                                                    .field("course_code")
                                                    .query(courseCode)
                                                    .fuzziness("2")
                                                    .prefixLength(3) // Preserve first 3 chars
                                                    .boost(6.0f)));

                                    return bb;
                                }));
                    }

                    // Common tags with high boost as they are explicit keywords
                    if (!CollectionUtils.isEmpty(sourceDoc.getTags())) {
                        b.should(should -> should
                                .terms(t -> t
                                        .field("tags")
                                        .terms(tt -> tt
                                                .value(sourceDoc.getTags().stream()
                                                        .map(FieldValue::of)
                                                        .collect(Collectors.toList())))
                                        .boost(4.0f)));
                    }

                    // Add preference-based boosts if preferences are available
                    if (preferences != null) {
                        // Apply content type weights if available
                        Map<String, Double> contentTypeWeights = preferences.getContentTypeWeights();
                        if (contentTypeWeights != null && !contentTypeWeights.isEmpty()) {
                            contentTypeWeights.forEach((type, weight) -> {
                                b.should(s -> s
                                        .bool(bb -> bb
                                                .must(m -> m.term(t -> t
                                                        .field("document_type")
                                                        .value(type)))
                                                .boost(weight.floatValue() * 2.0f) // Multiply by 2 to make it more significant
                                        )
                                );
                            });
                        }

                        // Boost preferred majors
                        if (CollectionUtils.isNotEmpty(preferences.getPreferredMajors())) {
                            b.should(s -> s
                                    .terms(t -> t
                                            .field("major")
                                            .terms(tt -> tt.value(preferences.getPreferredMajors().stream()
                                                    .map(FieldValue::of)
                                                    .collect(Collectors.toList())))
                                            .boost(2.0f)
                                    )
                            );
                        }

                        // Boost preferred levels
                        if (CollectionUtils.isNotEmpty(preferences.getPreferredLevels())) {
                            b.should(s -> s
                                    .terms(t -> t
                                            .field("course_level")
                                            .terms(tt -> tt.value(preferences.getPreferredLevels().stream()
                                                    .map(FieldValue::of)
                                                    .collect(Collectors.toList())))
                                            .boost(1.5f)
                                    )
                            );
                        }

                        // Boost preferred categories
                        if (CollectionUtils.isNotEmpty(preferences.getPreferredCategories())) {
                            b.should(s -> s
                                    .terms(t -> t
                                            .field("category")
                                            .terms(tt -> tt.value(preferences.getPreferredCategories().stream()
                                                    .map(FieldValue::of)
                                                    .collect(Collectors.toList())))
                                            .boost(1.8f)
                                    )
                            );
                        }

                        // Boost preferred tags
                        if (CollectionUtils.isNotEmpty(preferences.getPreferredTags())) {
                            b.should(s -> s
                                    .terms(t -> t
                                            .field("tags")
                                            .terms(tt -> tt.value(preferences.getPreferredTags().stream()
                                                    .map(FieldValue::of)
                                                    .collect(Collectors.toList())))
                                            .boost(1.5f)
                                    )
                            );
                        }

                        // Boost based on interaction history
                        if (preferences.getCategoryInteractionCounts() != null) {
                            preferences.getCategoryInteractionCounts().forEach((category, count) -> {
                                if (count > 0) {
                                    b.should(s -> s
                                            .term(t -> t
                                                    .field("category")
                                                    .value(category)
                                                    .boost(Math.min(count * 0.3f, 2.0f)) // Cap boost at 2.0
                                            )
                                    );
                                }
                            });
                        }
                    }

                    return b.minimumShouldMatch("1");
                }));

        // Create the search query
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(queryBuilder.build()._toQuery())
                .withPageable(PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Order.desc("_score")) // Sort by score in descending order
                ))
                .withTrackScores(true)
                .build();

        // Execute search
        SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                searchQuery,
                DocumentIndex.class
        );

        // Process results
        return processSearchResults(searchHits, pageable);
    }

    private Page<DocumentResponseDto> getPreferenceBasedRecommendations(
            String userId,
            DocumentPreferences preferences,
            Pageable pageable) {

        // Build the base query
        NativeQueryBuilder queryBuilder = NativeQuery.builder();

        // Add base filters
        queryBuilder.withQuery(q -> q
                .bool(b -> {
                    // Document must not be deleted and must be completed
                    b.must(m -> m
                            .bool(bb -> bb
                                    .must(mm -> mm.term(t -> t.field("deleted").value(false)))
                                    .must(mm -> mm.term(t -> t.field("status").value(DocumentStatus.COMPLETED.name())))
                            )
                    );

                    // Add access control
                    b.must(m -> m
                            .bool(bb -> bb
                                    .should(s -> s.term(t -> t.field("user_id").value(userId)))
                                    .should(s -> s.term(t -> t.field("sharing_type").value(SharingType.PUBLIC.name())))
                                    .should(s -> s
                                            .bool(bbb -> bbb
                                                    .must(mm -> mm.term(t -> t.field("sharing_type").value(SharingType.SPECIFIC.name())))
                                                    .must(mm -> mm.term(t -> t.field("shared_with").value(userId)))
                                            )
                                    )
                                    .minimumShouldMatch("1")
                            )
                    );

                    // Add boosts based on preferences
                    if (preferences != null) {
                        // Boost documents matching preferred majors
                        if (CollectionUtils.isNotEmpty(preferences.getPreferredMajors())) {
                            b.should(s -> s
                                    .terms(t -> t
                                            .field("major")
                                            .terms(tt -> tt.value(preferences.getPreferredMajors().stream()
                                                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                                    .collect(Collectors.toList())))
                                            .boost(3.0f)
                                    )
                            );
                        }

                        // Boost documents matching preferred levels
                        if (CollectionUtils.isNotEmpty(preferences.getPreferredLevels())) {
                            b.should(s -> s
                                    .terms(t -> t
                                            .field("course_level")
                                            .terms(tt -> tt.value(preferences.getPreferredLevels().stream()
                                                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                                    .collect(Collectors.toList())))
                                            .boost(2.0f)
                                    )
                            );
                        }

                        // Boost documents matching preferred categories
                        if (CollectionUtils.isNotEmpty(preferences.getPreferredCategories())) {
                            b.should(s -> s
                                    .terms(t -> t
                                            .field("category")
                                            .terms(tt -> tt.value(preferences.getPreferredCategories().stream()
                                                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                                    .collect(Collectors.toList())))
                                            .boost(2.5f)
                                    )
                            );
                        }

                        // Boost documents matching preferred tags
                        if (CollectionUtils.isNotEmpty(preferences.getPreferredTags())) {
                            b.should(s -> s
                                    .terms(t -> t
                                            .field("tags")
                                            .terms(tt -> tt.value(preferences.getPreferredTags().stream()
                                                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                                    .collect(Collectors.toList())))
                                            .boost(2.0f)
                                    )
                            );
                        }

                        // Apply content type weights if available
                        Map<String, Double> contentTypeWeights = preferences.getContentTypeWeights();
                        if (contentTypeWeights != null && !contentTypeWeights.isEmpty()) {
                            contentTypeWeights.forEach((type, weight) -> {
                                b.should(s -> s
                                        .bool(bb -> bb
                                                .must(m -> m.term(t -> t
                                                        .field("document_type")
                                                        .value(type)))
                                                .boost(weight.floatValue())
                                        )
                                );
                            });
                        }

                        // Boost based on interaction history
                        if (preferences.getCategoryInteractionCounts() != null) {
                            preferences.getCategoryInteractionCounts().forEach((category, count) -> {
                                if (count > 0) {
                                    b.should(s -> s
                                            .term(t -> t
                                                    .field("category")
                                                    .value(category)
                                                    .boost(Math.min(count * 0.5f, 3.0f)) // Cap boost at 3.0
                                            )
                                    );
                                }
                            });
                        }
                    }

                    return b;
                })
        );

        // Add pagination
        queryBuilder.withPageable(pageable);

        // Execute search
        SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                queryBuilder.build(),
                DocumentIndex.class
        );

        // Convert results
        List<DocumentResponseDto> documents = searchHits.getSearchHits().stream()
                .map(hit -> {
                    DocumentIndex doc = hit.getContent();
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
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(
                documents,
                pageable,
                searchHits.getTotalHits()
        );
    }
}