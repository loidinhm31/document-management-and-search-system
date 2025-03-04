package com.dms.document.search.service;

import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.SearchContext;
import com.dms.document.search.enums.DocumentReportStatus;
import com.dms.document.search.enums.DocumentType;
import com.dms.document.search.enums.QueryType;
import com.dms.document.search.enums.SharingType;
import com.dms.document.search.model.DocumentPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.common.lucene.search.function.CombineFunction;
import org.opensearch.common.lucene.search.function.FieldValueFactorFunction;
import org.opensearch.core.common.text.Text;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public abstract class OpenSearchBaseService {
    protected static final String INDEX_NAME = "documents";

    protected void addSharingAccessFilter(BoolQueryBuilder queryBuilder, String userId) {
        // Exclude deleted documents
        queryBuilder.filter(QueryBuilders.termQuery("deleted", false));

        // Add sharing access filters
        BoolQueryBuilder sharingFilter = QueryBuilders.boolQuery();

        // Owner access
        sharingFilter.should(QueryBuilders.termQuery("userId", userId));

        // Public access
        sharingFilter.should(QueryBuilders.termQuery("sharingType", SharingType.PUBLIC.name()));

        // Specific users access
        BoolQueryBuilder specificAccess = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("sharingType", SharingType.SPECIFIC.name()))
                .must(QueryBuilders.termsQuery("sharedWith", Collections.singletonList(userId)));
        sharingFilter.should(specificAccess);

        // Violation access: Exclude reported documents
        sharingFilter.mustNot(QueryBuilders.termQuery("reportStatus", DocumentReportStatus.RESOLVED.name()));

        sharingFilter.minimumShouldMatch(1);
        queryBuilder.filter(sharingFilter);
    }

    protected void addFilterConditions(BoolQueryBuilder queryBuilder, Set<String> majors, Set<String> courseCodes, String courseLevel, Set<String> categories, Set<String> tags) {
        if (CollectionUtils.isNotEmpty(majors)) {
            queryBuilder.filter(QueryBuilders.termsQuery("majors", majors));
        }
        if (CollectionUtils.isNotEmpty(courseCodes)) {
            queryBuilder.filter(QueryBuilders.termsQuery("courseCodes", courseCodes));
        }
        if (StringUtils.isNotBlank(courseLevel)) {
            queryBuilder.filter(QueryBuilders.termQuery("courseLevel", courseLevel));
        }
        if (CollectionUtils.isNotEmpty(categories)) {
            queryBuilder.filter(QueryBuilders.termsQuery("categories", categories));
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            queryBuilder.filter(QueryBuilders.termsQuery("tags", tags));
        }
    }

    protected HighlightBuilder configureSuggestionHighlightFields() {
        HighlightBuilder highlightBuilder = new HighlightBuilder();

        // Configure filename highlights
        highlightBuilder.field(new HighlightBuilder.Field("filename.analyzed")
                .preTags("@@HIGHLIGHT@@")
                .postTags("@@E_HIGHLIGHT@@")
                .fragmentSize(60)
                .numOfFragments(1)
                .boundaryMaxScan(128)
                .phraseLimit(256));

        highlightBuilder.field(new HighlightBuilder.Field("filename.search")
                .preTags("@@HIGHLIGHT@@")
                .postTags("@@E_HIGHLIGHT@@")
                .fragmentSize(60)
                .numOfFragments(1)
                .boundaryMaxScan(128)
                .phraseLimit(256));

        // Configure content highlights
        highlightBuilder.field(new HighlightBuilder.Field("content")
                .preTags("@@HIGHLIGHT@@")
                .postTags("@@E_HIGHLIGHT@@")
                .fragmentSize(150)
                .numOfFragments(1)
                .boundaryMaxScan(128)
                .phraseLimit(256));

        return highlightBuilder;
    }

    protected HighlightBuilder configureHighlightFields(SearchContext context) {
        HighlightBuilder highlightBuilder = new HighlightBuilder();

        // Configure filename highlights
        highlightBuilder.field(new HighlightBuilder.Field("filename.analyzed")
                .preTags("<em><b>")
                .postTags("</b></em>")
                .fragmentSize(60)
                .numOfFragments(1));

        highlightBuilder.field(new HighlightBuilder.Field("filename.search")
                .preTags("<em><b>")
                .postTags("</b></em>")
                .fragmentSize(60)
                .numOfFragments(1));

        // Configure content highlights
        highlightBuilder.field(new HighlightBuilder.Field("content")
                .preTags("<em><b>")
                .postTags("</b></em>")
                .fragmentSize(context.queryType() == QueryType.DEFINITION ? 200 : 150)
                .numOfFragments(context.queryType() == QueryType.DEFINITION ? 1 : 2));

        return highlightBuilder;
    }

    protected Page<DocumentResponseDto> processSearchResults(SearchHit[] searchHits, long totalHits, Pageable pageable) {
        List<DocumentResponseDto> documentResponses = Arrays.stream(searchHits)
                .map(hit -> {
                    Map<String, Object> source = hit.getSourceAsMap();
                    List<String> highlights = new ArrayList<>();

                    // Process highlights safely
                    Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                    if (highlightFields != null) {
                        addHighlightsFromField(highlightFields.get("filename.analyzed"), highlights);
                        addHighlightsFromField(highlightFields.get("filename.search"), highlights);
                        addHighlightsFromField(highlightFields.get("content"), highlights);
                    }

                    // Handle createdAt date mapping correctly
                    Date createdAt = null;
                    Object createdAtObj = source.get("createdAt");
                    if (createdAtObj != null) {
                        if (createdAtObj instanceof String) {
                            // Parse ISO date string
                            try {
                                createdAt = Date.from(Instant.parse((String) createdAtObj));
                            } catch (DateTimeParseException e) {
                                log.warn("Failed to parse createdAt date string: {}", createdAtObj);
                            }
                        } else if (createdAtObj instanceof Number) {
                            // Handle timestamp in milliseconds
                            createdAt = new Date(((Number) createdAtObj).longValue());
                        }
                    }

                    return DocumentResponseDto.builder()
                            .id(hit.getId())
                            .filename(Optional.ofNullable(source.get("filename"))
                                    .map(Object::toString)
                                    .orElse(""))
                            .documentType(Optional.ofNullable(source.get("documentType"))
                                    .map(Object::toString)
                                    .map(DocumentType::valueOf)
                                    .orElse(null))
                            .majors(Optional.ofNullable(source.get("majors"))
                                    .map(obj -> {
                                        if (obj instanceof Collection) {
                                            return new HashSet<>((Collection<String>) obj);
                                        }
                                        return new HashSet<String>();
                                    })
                                    .orElse(new HashSet<>()))
                            .courseCodes(Optional.ofNullable(source.get("courseCodes"))
                                    .map(obj -> {
                                        if (obj instanceof Collection) {
                                            return new HashSet<>((Collection<String>) obj);
                                        }
                                        return new HashSet<String>();
                                    })
                                    .orElse(new HashSet<>()))
                            .courseLevel(Optional.ofNullable(source.get("courseLevel"))
                                    .map(Object::toString)
                                    .orElse(""))
                            .categories(Optional.ofNullable(source.get("categories"))
                                    .map(obj -> {
                                        if (obj instanceof Collection) {
                                            return new HashSet<>((Collection<String>) obj);
                                        }
                                        return new HashSet<String>();
                                    })
                                    .orElse(new HashSet<>()))
                            .tags(Optional.ofNullable(source.get("tags"))
                                    .map(obj -> {
                                        if (obj instanceof Collection) {
                                            return new HashSet<>((Collection<String>) obj);
                                        }
                                        return new HashSet<String>();
                                    })
                                    .orElse(new HashSet<>()))
                            .fileSize(Optional.ofNullable(source.get("fileSize"))
                                    .map(size -> ((Number) size).longValue())
                                    .orElse(0L))
                            .mimeType(Optional.ofNullable(source.get("mimeType"))
                                    .map(Object::toString)
                                    .orElse(""))
                            .language(Optional.ofNullable(source.get("language"))
                                    .map(Object::toString)
                                    .orElse(""))
                            .userId(Optional.ofNullable(source.get("userId"))
                                    .map(Object::toString)
                                    .orElse(""))
                            .createdAt(createdAt)
                            .highlights(highlights)
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(documentResponses, pageable, totalHits);
    }

    protected List<String> processSuggestionResults(SearchHit[] hits) {
        return Arrays.stream(hits)
                .map(hit -> {
                    List<String> highlights = new ArrayList<>();

                    Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                    if (highlightFields != null) {
                        addHighlightsFromField(highlightFields.get("filename.analyzed"), highlights);
                        addHighlightsFromField(highlightFields.get("filename.search"), highlights);
                        addHighlightsFromField(highlightFields.get("content"), highlights);
                    }
                    return highlights;
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    protected void addRecommendationBoost(BoolQueryBuilder queryBuilder) {
        // Boost documents based on recommendation count
        FieldValueFactorFunctionBuilder recommendationFactor = new FieldValueFactorFunctionBuilder("recommendationCount")
                .factor(1.0f)
                .modifier(FieldValueFactorFunction.Modifier.LOG1P) // Use log(1 + x) to smooth the curve
                .missing(0); // Default value if field is missing
        queryBuilder.should(
                QueryBuilders.functionScoreQuery(
                                QueryBuilders.rangeQuery("recommendationCount").gt(0),
                                recommendationFactor
                        ).boostMode(CombineFunction.MULTIPLY)
                        .boost(5.0f)
        );
    }

    protected void addPreferredFieldBoost(BoolQueryBuilder queryBuilder, String field, Set<String> values, float boost) {
        if (CollectionUtils.isNotEmpty(values)) {
            queryBuilder.should(QueryBuilders.termsQuery(field, values)
                    .boost(boost));
        }
    }

    protected void addBasicPreferenceBoosts(BoolQueryBuilder queryBuilder, DocumentPreferences preferences) {
        if (preferences == null) return;

        // Add preferred fields with moderate boost values
        // Note: These are intentionally lower than search term boosts (which go up to 15.0f)
        // to ensure search relevance remains the primary factor
        addPreferredFieldBoost(queryBuilder, "major", preferences.getPreferredMajors(), 1.5f);
        addPreferredFieldBoost(queryBuilder, "courseCode", preferences.getPreferredCourseCodes(), 1.5f);
        addPreferredFieldBoost(queryBuilder, "courseLevel", preferences.getPreferredLevels(), 1.0f);
        addPreferredFieldBoost(queryBuilder, "category", preferences.getPreferredCategories(), 1.0f);
        addPreferredFieldBoost(queryBuilder, "tags", preferences.getPreferredTags(), 1.0f);

        // Language preferences
        if (CollectionUtils.isNotEmpty(preferences.getLanguagePreferences())) {
            queryBuilder.should(QueryBuilders.termsQuery("language", preferences.getLanguagePreferences())
                    .boost(1.5f));
        }
    }

    private void addHighlightsFromField(HighlightField field, List<String> highlights) {
        if (field != null && field.fragments() != null) {
            Arrays.stream(field.fragments())
                    .map(Text::string)
                    .forEach(highlights::add);
        }
    }
}