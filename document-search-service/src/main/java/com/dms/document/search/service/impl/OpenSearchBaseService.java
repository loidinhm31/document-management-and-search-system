package com.dms.document.search.service.impl;

import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.RoleResponse;
import com.dms.document.search.dto.SearchContext;
import com.dms.document.search.enums.*;
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
    public static final int MAX_SUGGESTIONS = 10;

    protected void addSharingAccessFilter(BoolQueryBuilder queryBuilder, String userId, AppRole userRole) {
        // Exclude deleted documents
        queryBuilder.filter(QueryBuilders.termQuery("deleted", false));

        // Add sharing access filters
        BoolQueryBuilder sharingFilter = QueryBuilders.boolQuery();

        if (!userRole.equals(AppRole.ROLE_ADMIN)) {
            // Owner access
            sharingFilter.should(QueryBuilders.termQuery("userId", userId));

            // Public access
            sharingFilter.should(QueryBuilders.termQuery("sharingType", SharingType.PUBLIC.name()));

            // Specific users access
            BoolQueryBuilder specificAccess = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("sharingType", SharingType.SPECIFIC.name()))
                    .must(QueryBuilders.termsQuery("sharedWith", Collections.singletonList(userId)));
            sharingFilter.should(specificAccess);
        }

        // Violation access: Exclude reported documents
        sharingFilter.mustNot(QueryBuilders.termQuery("reportStatus", DocumentReportStatus.RESOLVED.name()));

        sharingFilter.minimumShouldMatch(1);
        queryBuilder.filter(sharingFilter);
    }

    protected void addFilterConditions(BoolQueryBuilder queryBuilder, Set<String> majors, Set<String> courseCodes, String courseLevel, Set<String> categories, Set<String> tags) {
        // Keep original filter conditions to ensure basic matching
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

        // Add scoring boosts for individual term matches
        if (CollectionUtils.isNotEmpty(majors)) {
            for (String major : majors) {
                queryBuilder.should(QueryBuilders.termQuery("majors", major).boost(1.0f));
            }
        }
        if (CollectionUtils.isNotEmpty(courseCodes)) {
            for (String courseCode : courseCodes) {
                queryBuilder.should(QueryBuilders.termQuery("courseCodes", courseCode).boost(1.0f));
            }
        }
        if (StringUtils.isNotBlank(courseLevel)) {
            queryBuilder.should(QueryBuilders.termQuery("courseLevel", courseLevel).boost(1.0f));
        }
        if (CollectionUtils.isNotEmpty(categories)) {
            for (String category : categories) {
                queryBuilder.should(QueryBuilders.termQuery("categories", category).boost(1.0f));
            }
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryBuilder.should(QueryBuilders.termQuery("tags", tag).boost(1.0f));
            }
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

                    Date updatedAt = null;
                    Object updatedAtObj = source.get("updatedAt");
                    if (updatedAtObj != null) {
                        if (updatedAtObj instanceof String) {
                            // Parse ISO date string
                            try {
                                updatedAt = Date.from(Instant.parse((String) updatedAtObj));
                            } catch (DateTimeParseException e) {
                                log.warn("Failed to parse updatedAt date string: {}", updatedAtObj);
                            }
                        } else if (updatedAtObj instanceof Number) {
                            // Handle timestamp in milliseconds
                            updatedAt = new Date(((Number) createdAtObj).longValue());
                        }
                    }

                    return DocumentResponseDto.builder()
                            .id(hit.getId())
                            .status(Optional.ofNullable(source.get("status"))
                                    .map(Object::toString)
                                    .orElse(""))
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
                            .updatedAt(updatedAt)
                            .currentVersion(Optional.ofNullable(source.get("currentVersion"))
                                    .map(size -> ((Number) size).intValue())
                                    .orElse(0))
                            .highlights(highlights)
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(documentResponses, pageable, totalHits);
    }

    protected List<String> processSuggestionResults(SearchHit[] hits) {
        // Collect content highlights first (higher priority)
        List<String> contentHighlights = new ArrayList<>();
        // Collect filename highlights second (lower priority)
        List<String> filenameHighlights = new ArrayList<>();

        for (SearchHit hit : hits) {
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            if (highlightFields != null) {
                // Process content highlights
                HighlightField contentField = highlightFields.get("content");
                if (contentField != null && contentField.fragments() != null) {
                    Arrays.stream(contentField.fragments())
                            .map(Text::string)
                            .forEach(contentHighlights::add);
                }

                // Process filename highlights
                HighlightField filenameAnalyzedField = highlightFields.get("filename.analyzed");
                HighlightField filenameSearchField = highlightFields.get("filename.search");

                if (filenameAnalyzedField != null && filenameAnalyzedField.fragments() != null) {
                    Arrays.stream(filenameAnalyzedField.fragments())
                            .map(Text::string)
                            .forEach(filenameHighlights::add);
                }

                if (filenameSearchField != null && filenameSearchField.fragments() != null) {
                    Arrays.stream(filenameSearchField.fragments())
                            .map(Text::string)
                            .forEach(filenameHighlights::add);
                }
            }
        }

        // Remove duplicates from both lists
        List<String> uniqueContentHighlights = contentHighlights.stream().distinct().toList();
        List<String> uniqueFilenameHighlights = filenameHighlights.stream().distinct().toList();

        // Combine with priority to content highlights, up to MAX_SUGGESTIONS
        List<String> result = new ArrayList<>(uniqueContentHighlights);

        // If we have fewer than MAX_SUGGESTIONS from content, add filename highlights
        if (result.size() < MAX_SUGGESTIONS) {
            int remainingSlots = MAX_SUGGESTIONS - result.size();
            result.addAll(uniqueFilenameHighlights.stream()
                    .limit(remainingSlots)
                    .toList());
        }

        return result.stream()
                .limit(MAX_SUGGESTIONS)
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
        addPreferredFieldBoost(queryBuilder, "majors", preferences.getPreferredMajors(), 1.5f);
        addPreferredFieldBoost(queryBuilder, "courseCodes", preferences.getPreferredCourseCodes(), 1.5f);
        addPreferredFieldBoost(queryBuilder, "courseLevel", preferences.getPreferredLevels(), 1.0f);
        addPreferredFieldBoost(queryBuilder, "categories", preferences.getPreferredCategories(), 1.0f);
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