package com.sdms.document.service;

import com.sdms.document.elasticsearch.DocumentIndex;
import com.sdms.document.entity.User;
import com.sdms.document.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;
import co.elastic.clients.elasticsearch._types.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSearchService {
    private final ElasticsearchOperations elasticsearchOperations;
    private final UserRepository userRepository;

    public Page<DocumentIndex> searchDocuments(String query, String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidDataAccessResourceUsageException("User not found"));

        String userId = user.getUserId().toString();
        log.info("Searching documents for userId: {}", userId);

        // Create search query with relaxed content matching if query is provided
        Query searchQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> {
                            // Add query condition only if search query is provided
                            if (query != null && !query.trim().isEmpty()) {
                                b.must(m -> m
                                        .match(t -> t
                                                .field("content")
                                                .query(query)
                                        )
                                );
                            }
                            // Add user filter
                            return b.filter(f -> f
                                    .term(t -> t
                                            .field("userId.keyword")
                                            .value(userId)
                                    )
                            );
                        })
                )
                .withSort(s -> s
                        .field(f -> f
                                .field("createdAt")
                                .order(SortOrder.Desc)
                        )
                )
                .withPageable(pageable)
                .build();

        // Execute search
        SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                searchQuery,
                DocumentIndex.class
        );

        log.info("Found {} documents", searchHits.getTotalHits());

        // Convert SearchHits to List<DocumentIndex>
        List<DocumentIndex> documents = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .peek(doc -> {
                    doc.setContent(null); // Remove content for response
                    log.debug("Processing document with id: {}", doc.getId());
                })
                .collect(Collectors.toList());

        // Create Page object
        return new PageImpl<>(
                documents,
                pageable,
                searchHits.getTotalHits()
        );
    }
}