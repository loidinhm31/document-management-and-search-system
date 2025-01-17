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
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSearchService {
    private final ElasticsearchOperations elasticsearchOperations;
    private final UserRepository userRepository;

    public Page<DocumentIndex> searchDocuments(String searchQuery, String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidDataAccessResourceUsageException("User not found"));

        String userId = user.getUserId().toString();
        log.info("Searching documents for userId: {} with query: {}", userId, searchQuery);

        try {
            // Build the query using Query DSL
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(q -> q
                            .bool(b -> b
                                    .must(m -> m
                                            .match(match -> match
                                                    .field("userId.keyword")
                                                    .query(userId)
                                            )
                                    )
                                    .must(m -> m
                                            .bool(innerBool -> innerBool
                                                    .should(s -> s
                                                            .match(match -> match
                                                                    .field("content")
                                                                    .query(searchQuery)
                                                            )
                                                    )
                                                    .should(s -> s
                                                            .match(match -> match
                                                                    .field("filename")
                                                                    .query(searchQuery)
                                                            )
                                                    )
                                                    .minimumShouldMatch("1")
                                            )
                                    )
                            )
                    )
                    .withPageable(pageable)
                    .build();

            // Execute search
            SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(
                    nativeQuery,
                    DocumentIndex.class
            );

            log.info("Found {} documents", searchHits.getTotalHits());

            // Convert SearchHits to List<DocumentIndex>
            List<DocumentIndex> documents = searchHits.getSearchHits().stream()
                    .map(hit -> {
                        DocumentIndex doc = hit.getContent();
                        if (doc.getContent() != null && doc.getContent().length() > 200) {
                            doc.setContent(doc.getContent().substring(0, 200) + "...");
                        }
                        return doc;
                    })
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
}