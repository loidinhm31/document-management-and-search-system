package com.dms.document.search.service.impl;

import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.UserResponse;
import com.dms.document.search.exception.InvalidDocumentException;
import com.dms.document.search.model.DocumentPreferences;
import com.dms.document.search.repository.DocumentPreferencesRepository;
import com.dms.document.search.service.DocumentFavoriteService;
import com.dms.document.search.service.DocumentRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MoreLikeThisQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRecommendationServiceImpl extends OpenSearchBaseService implements DocumentRecommendationService {
    private final RestHighLevelClient openSearchClient;
    private final UserClient userClient;
    private final DocumentPreferencesRepository documentPreferencesRepository;
    private final DocumentFavoriteService documentFavoriteService;

    private static final float MAX_INTERACTION_BOOST = 3.0f;
    private static final float INTERACTION_WEIGHT_MULTIPLIER = 0.5f;
    private static final float PREFERENCE_BOOST_MULTIPLIER = 2.0f;

    @Override
    public Page<DocumentResponseDto> getRecommendations(String documentId, Boolean favoriteOnly, String username, Pageable pageable) {
        try {
            ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
            if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
                throw new InvalidDataAccessResourceUsageException("User not found");
            }

            UserResponse userResponse = response.getBody();
            DocumentPreferences preferences = getOrCreatePreferences(userResponse.userId().toString());

            return StringUtils.isNotEmpty(documentId)
                    ? getContentBasedRecommendations(documentId, userResponse.userId().toString(), preferences, pageable)
                    : getPreferenceBasedRecommendations(userResponse.userId(), preferences, favoriteOnly, pageable);
        } catch (IOException e) {
            log.error("Error getting recommendations: {}", e.getMessage());
            throw new RuntimeException("Failed to get recommendations", e);
        }
    }

    private DocumentPreferences getOrCreatePreferences(String userId) {
        return documentPreferencesRepository.findByUserId(userId)
                .orElseGet(() -> {
                    DocumentPreferences defaultPrefs = new DocumentPreferences();
                    defaultPrefs.setUserId(userId);
                    defaultPrefs.setCreatedAt(Instant.now());
                    defaultPrefs.setUpdatedAt(Instant.now());
                    return documentPreferencesRepository.save(defaultPrefs);
                });
    }

    private Page<DocumentResponseDto> getContentBasedRecommendations(
            String documentId,
            String userId,
            DocumentPreferences preferences,
            Pageable pageable) throws IOException {

        // Get source document
        GetResponse sourceDoc = openSearchClient.get(
                new GetRequest(INDEX_NAME, documentId),
                RequestOptions.DEFAULT
        );

        if (!sourceDoc.isExists()) {
            throw new InvalidDocumentException("Document not found");
        }

        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        // Add sharing access filter
        addSharingAccessFilter(queryBuilder, userId);

        // Exclude source document
        queryBuilder.mustNot(QueryBuilders.termQuery("_id", documentId));

        // Boost documents based on recommendation count
        addRecommendationBoost(queryBuilder);

        // Content similarity
        addContentSimilarityBoosts(queryBuilder, sourceDoc.getSourceAsMap());

        // Metadata similarity
        addMetadataSimilarityBoosts(queryBuilder, sourceDoc.getSourceAsMap());

        // Add preference boosts
        addPreferenceBoosts(queryBuilder, preferences);

        // Add language preferences
        if (CollectionUtils.isNotEmpty(preferences.getLanguagePreferences())) {
            queryBuilder.should(QueryBuilders.termsQuery("language", preferences.getLanguagePreferences()).boost(5.0f));
        }

        // Add recent views boost
        if (CollectionUtils.isNotEmpty(preferences.getRecentViewedDocuments())) {
            queryBuilder.should(QueryBuilders.termsQuery("_id", preferences.getRecentViewedDocuments()).boost(1.5f));
        }

        // Configure search request
        searchSourceBuilder.query(queryBuilder)
                .from(pageable.getPageNumber() * pageable.getPageSize())
                .size(pageable.getPageSize())
                .trackScores(true)
                .sort(SortBuilders.scoreSort().order(SortOrder.DESC))
                .fetchSource(new String[]{"*"}, new String[]{"content"});

        searchRequest.source(searchSourceBuilder);

        // Execute search
        SearchResponse searchResponse = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);

        return processSearchResults(
                searchResponse.getHits().getHits(),
                Objects.nonNull(searchResponse.getHits().getTotalHits()) ? searchResponse.getHits().getTotalHits().value : 0,
                pageable
        );
    }

    private void addContentSimilarityBoosts(BoolQueryBuilder queryBuilder, Map<String, Object> sourceDoc) {
        String content = (String) sourceDoc.get("content");
        String filename = (String) sourceDoc.get("filename");

        // Content similarity using more-like-this
        if (StringUtils.isNotEmpty(content)) {
            MoreLikeThisQueryBuilder.Item[] contentItems = new MoreLikeThisQueryBuilder.Item[]{
                    new MoreLikeThisQueryBuilder.Item(INDEX_NAME, content)
            };

            queryBuilder.should(QueryBuilders.moreLikeThisQuery(
                            new String[]{"content"},
                            null,  // no like text
                            contentItems) // using items instead
                    .minTermFreq(2)
                    .minDocFreq(1)
                    .maxQueryTerms(25)
                    .minimumShouldMatch("30%")
                    .boost(10.0f));
        }

        // Title similarity
        if (StringUtils.isNotEmpty(filename)) {
            MoreLikeThisQueryBuilder.Item[] filenameItems = new MoreLikeThisQueryBuilder.Item[]{
                    new MoreLikeThisQueryBuilder.Item(INDEX_NAME, filename)
            };

            queryBuilder.should(QueryBuilders.moreLikeThisQuery(
                            new String[]{"filename.analyzed"},
                            null,  // no like text
                            filenameItems) // using items instead
                    .minTermFreq(1)
                    .minDocFreq(1)
                    .maxQueryTerms(10)
                    .boost(5.0f));
        }
    }

    private void addMetadataSimilarityBoosts(BoolQueryBuilder queryBuilder, Map<String, Object> sourceDoc) {
        @SuppressWarnings("unchecked")
        List<String> majors = (List<String>) sourceDoc.get("majors");
        String courseLevel = (String) sourceDoc.get("courseLevel");
        if (majors != null) {
            queryBuilder.should(QueryBuilders.termsQuery("majors", majors).boost(3.0f));
        }

        @SuppressWarnings("unchecked")
        List<String> categories = (List<String>) sourceDoc.get("categories");
        if (categories != null) {
            queryBuilder.should(QueryBuilders.termsQuery("categories", categories).boost(2.0f));
        }

        @SuppressWarnings("unchecked")
        List<String> courseCodes = (List<String>) sourceDoc.get("courseCodes");
        if (courseCodes != null) {
            queryBuilder.should(QueryBuilders.termsQuery("courseCodes", courseCodes).boost(3.0f));
        }

        if (courseLevel != null) {
            queryBuilder.should(QueryBuilders.termQuery("courseLevel", courseLevel).boost(3.0f));
        }

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) sourceDoc.get("tags");
        if (CollectionUtils.isNotEmpty(tags)) {
            queryBuilder.should(QueryBuilders.termsQuery("tags", tags).boost(4.0f));
        }

        @SuppressWarnings("unchecked")
        Map<String, String> extractedMetadata = (Map<String, String>) sourceDoc.get("extractedMetadata");
        if (extractedMetadata != null) {
            extractedMetadata.forEach((key, value) -> {
                queryBuilder.should(QueryBuilders.termQuery("extractedMetadata.value", value).boost(2.0f));
            });
        }
    }

    private void addPreferenceBoosts(BoolQueryBuilder queryBuilder, DocumentPreferences preferences) {
        if (preferences == null) return;

        // Add preferred majors boost
        addPreferredFieldBoost(queryBuilder, "major", preferences.getPreferredMajors(), 3.0f * PREFERENCE_BOOST_MULTIPLIER);

        // Add preferred course code boost
        addPreferredFieldBoost(queryBuilder, "courseCode", preferences.getPreferredCourseCodes(), 3.0f * PREFERENCE_BOOST_MULTIPLIER);

        // Add preferred levels boost
        addPreferredFieldBoost(queryBuilder, "courseLevel", preferences.getPreferredLevels(), 2.0f * PREFERENCE_BOOST_MULTIPLIER);

        // Add preferred categories boost
        addPreferredFieldBoost(queryBuilder, "category", preferences.getPreferredCategories(), 2.5f * PREFERENCE_BOOST_MULTIPLIER);

        // Add preferred tags boost
        addPreferredFieldBoost(queryBuilder, "tags", preferences.getPreferredTags(), 2.0f * PREFERENCE_BOOST_MULTIPLIER);

        // Add content type weights
        addContentTypeWeights(queryBuilder, preferences.getContentTypeWeights());

        // Add interaction history boosts
        addInteractionHistoryBoosts(queryBuilder, preferences);
    }

    private void addContentTypeWeights(BoolQueryBuilder queryBuilder, Map<String, Double> contentTypeWeights) {
        if (contentTypeWeights != null && !contentTypeWeights.isEmpty()) {
            contentTypeWeights.forEach((type, weight) -> {
                if (weight > 0) {
                    queryBuilder.should(QueryBuilders.termQuery("document_type", type)
                            .boost((float) (weight * PREFERENCE_BOOST_MULTIPLIER)));
                }
            });
        }
    }

    private void addInteractionHistoryBoosts(BoolQueryBuilder queryBuilder, DocumentPreferences preferences) {
        addInteractionCountBoosts(queryBuilder, "category", preferences.getCategoryInteractionCounts());
        addInteractionCountBoosts(queryBuilder, "major", preferences.getMajorInteractionCounts());
        addInteractionCountBoosts(queryBuilder, "courseLevel", preferences.getLevelInteractionCounts());
        addInteractionCountBoosts(queryBuilder, "tags", preferences.getTagInteractionCounts());
    }

    private void addInteractionCountBoosts(BoolQueryBuilder queryBuilder, String field,
                                           Map<String, Integer> interactionCounts) {
        if (interactionCounts != null) {
            interactionCounts.forEach((value, count) -> {
                if (count > 0) {
                    float boost = Math.min(count * INTERACTION_WEIGHT_MULTIPLIER, MAX_INTERACTION_BOOST);
                    queryBuilder.should(QueryBuilders.termQuery(field, value).boost(boost));
                }
            });
        }
    }

    private Page<DocumentResponseDto> getPreferenceBasedRecommendations(
            UUID userId,
            DocumentPreferences preferences,
            Boolean favoriteOnly,
            Pageable pageable) throws IOException {

        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        // Add sharing access filter
        addSharingAccessFilter(queryBuilder, userId.toString());

        // Add favorite filter if requested
        if (Boolean.TRUE.equals(favoriteOnly)) {
            documentFavoriteService.addFavoriteFilter(queryBuilder, userId);
        }

        // Add preference-based boosts
        addPreferenceBoosts(queryBuilder, preferences);

        // Add language preferences
        if (CollectionUtils.isNotEmpty(preferences.getLanguagePreferences())) {
            queryBuilder.should(QueryBuilders.termsQuery("language", preferences.getLanguagePreferences()).boost(5.0f));
        }

        // Add recent views boost
        if (CollectionUtils.isNotEmpty(preferences.getRecentViewedDocuments())) {
            queryBuilder.should(QueryBuilders.termsQuery("_id", preferences.getRecentViewedDocuments()).boost(1.5f));
        }

        searchSourceBuilder.query(queryBuilder)
                .from(pageable.getPageNumber() * pageable.getPageSize())
                .size(pageable.getPageSize())
                .trackScores(true)
                .sort(SortBuilders.scoreSort().order(SortOrder.DESC));

        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);

        return processSearchResults(
                searchResponse.getHits().getHits(),
                Objects.nonNull(searchResponse.getHits().getTotalHits()) ? searchResponse.getHits().getTotalHits().value : 0,
                pageable
        );
    }
}