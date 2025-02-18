package com.dms.document.search.service;

import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.DocumentSearchRequest;
import com.dms.document.search.dto.SearchContext;
import com.dms.document.search.enums.DocumentType;
import com.dms.document.search.enums.QueryType;
import com.dms.document.search.enums.SharingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.core.common.text.Text;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
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

        sharingFilter.minimumShouldMatch(1);
        queryBuilder.filter(sharingFilter);
    }

    protected void addFilterConditions(BoolQueryBuilder queryBuilder, String major, String courseLevel, String category, Set<String> tags) {
        if (StringUtils.isNotBlank(major)) {
            queryBuilder.filter(QueryBuilders.termQuery("major", major));
        }

        if (StringUtils.isNotBlank(courseLevel)) {
            queryBuilder.filter(QueryBuilders.termQuery("courseLevel", courseLevel));
        }

        if (StringUtils.isNotBlank(category)) {
            queryBuilder.filter(QueryBuilders.termQuery("category", category));
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
                            // Handle String fields with defaults
                            .filename(Optional.ofNullable(source.get("filename"))
                                    .map(Object::toString)
                                    .orElse(""))
                            // Handle DocumentType enum safely
                            .documentType(Optional.ofNullable(source.get("documentType"))
                                    .map(Object::toString)
                                    .map(DocumentType::valueOf)
                                    .orElse(null))
                            .major(Optional.ofNullable(source.get("major"))
                                    .map(Object::toString)
                                    .orElse(""))
                            .courseCode(Optional.ofNullable(source.get("courseCode"))
                                    .map(Object::toString)
                                    .orElse(""))
                            .courseLevel(Optional.ofNullable(source.get("courseLevel"))
                                    .map(Object::toString)
                                    .orElse(""))
                            .category(Optional.ofNullable(source.get("category"))
                                    .map(Object::toString)
                                    .orElse(""))
                            // Handle numeric fields safely
                            .fileSize(Optional.ofNullable(source.get("fileSize"))
                                    .map(size -> {
                                        if (size instanceof Long) return (Long) size;
                                        if (size instanceof Integer) return ((Integer) size).longValue();
                                        return 0L;
                                    })
                                    .orElse(0L))
                            // Handle String fields with defaults
                            .mimeType(Optional.ofNullable(source.get("mimeType"))
                                    .map(Object::toString)
                                    .orElse(""))
                            .language(Optional.ofNullable(source.get("language"))
                                    .map(Object::toString)
                                    .orElse(""))
                            .userId(Optional.ofNullable(source.get("userId"))
                                    .map(Object::toString)
                                    .orElse(""))
                            // Handle collections safely
                            .tags(Optional.ofNullable(source.get("tags"))
                                    .map(obj -> {
                                        if (obj instanceof List<?>) {
                                            return new HashSet<>((List<String>) obj);
                                        }
                                        return new HashSet<String>();
                                    })
                                    .orElse(new HashSet<>()))
                            .createdAt(createdAt)
                            // Handle highlights list
                            .highlights(highlights)
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(documentResponses, pageable, totalHits);
    }

    public List<String> processSuggestionResults(SearchHit[] hits) {
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

    private void addHighlightsFromField(HighlightField field, List<String> highlights) {
        if (field != null && field.fragments() != null) {
            Arrays.stream(field.fragments())
                    .map(Text::string)
                    .forEach(highlights::add);
        }
    }
}