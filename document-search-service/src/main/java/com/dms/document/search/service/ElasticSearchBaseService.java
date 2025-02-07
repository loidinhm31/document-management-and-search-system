package com.dms.document.search.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.elasticsearch.DocumentIndex;
import com.dms.document.search.enums.SharingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ElasticSearchBaseService {
    protected void addSharingAccessFilter(BoolQuery.Builder queryBuilder, String userId) {
        // Exclude deleted documents
        queryBuilder.filter(f -> f
                .term(t -> t
                        .field("deleted")
                        .value(false)));

        // Add sharing access filters
        queryBuilder.filter(f -> f
                .bool(b -> b.should(s -> s
                                // Owner access
                                .term(t -> t
                                        .field("user_id")
                                        .value(userId)))
                        .should(s -> s
                                // Public access
                                .term(t -> t
                                        .field("sharing_type")
                                        .value(SharingType.PUBLIC.name())))
                        .should(s -> s
                                // Specific users access
                                .bool(sb -> sb
                                        .must(m -> m
                                                .term(t -> t
                                                        .field("sharing_type")
                                                        .value(SharingType.SPECIFIC.name())))
                                        .must(m -> m
                                                .terms(t -> t
                                                        .field("shared_with")
                                                        .terms(tt -> tt
                                                                .value(Collections.singletonList(FieldValue.of(userId))))))))
                        .minimumShouldMatch("1")));
    }

    protected Page<DocumentResponseDto> processSearchResults(SearchHits<DocumentIndex> searchHits, Pageable pageable) {
        List<DocumentResponseDto> documentResponses = searchHits.getSearchHits().stream()
                .map(hit -> {
                    DocumentIndex doc = hit.getContent();
                    List<String> highlights = new ArrayList<>();

                    // Collect all highlights
                    Map<String, List<String>> highlightFields = hit.getHighlightFields();
                    // First add filename highlights if they exist
                    addHighlightsFromField(highlightFields, "filename.analyzed", highlights);
                    addHighlightsFromField(highlightFields, "filename.search", highlights);

                    // Then add content highlights
                    addHighlightsFromField(highlightFields, "content", highlights);

                    // Build the response DTO
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
                            .highlights(highlights)
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(
                documentResponses,
                pageable,
                searchHits.getTotalHits()
        );
    }

    private void addHighlightsFromField(Map<String, List<String>> highlightFields, String fieldName, List<String> highlights) {
        List<String> fieldHighlights = highlightFields.get(fieldName);
        if (fieldHighlights != null && !fieldHighlights.isEmpty()) {
            highlights.addAll(fieldHighlights);
        }
    }
}
