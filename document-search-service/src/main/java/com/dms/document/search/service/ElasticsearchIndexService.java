package com.dms.document.search.service;

import com.dms.document.search.elasticsearch.DocumentIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElasticsearchIndexService {
    private final ElasticsearchOperations elasticsearchOperations;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void setupIndex() {
        try {
            // Check if index exists
            boolean indexExists = elasticsearchOperations.indexOps(DocumentIndex.class).exists();

            if (indexExists) {
                log.info("Index exists. Verifying mapping...");
                verifyIndexMapping();
            } else {
                log.info("Creating index with mapping and settings...");
                // Create index with settings and mapping
                elasticsearchOperations.indexOps(DocumentIndex.class).create();

                // Apply mapping explicitly if not applied during creation
                elasticsearchOperations.indexOps(DocumentIndex.class).putMapping();

                // Refresh the index
                elasticsearchOperations.indexOps(DocumentIndex.class).refresh();

                // Verify the newly created mapping
                verifyIndexMapping();

                log.info("Index created successfully");
            }
        } catch (Exception e) {
            log.error("Error setting up Elasticsearch index", e);
            throw new RuntimeException("Failed to setup Elasticsearch index", e);
        }
    }

    public void verifyIndexMapping() {
        try {
            // Get mapping
            Map<String, Object> mapping = elasticsearchOperations.indexOps(DocumentIndex.class).getMapping();
            String mappingJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapping);
            log.info("Current mapping: {}", mappingJson);

            // Get settings
            Map<String, Object> settings = elasticsearchOperations.indexOps(DocumentIndex.class).getSettings();
            String settingsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings);
            log.info("Current settings: {}", settingsJson);

            // Verify specific fields exist
            Map<String, Object> properties = getPropertiesFromMapping(mapping);
            verifyField(properties, "filename");
            verifyField(properties, "content");

        } catch (Exception e) {
            log.error("Error verifying index mapping", e);
            throw new RuntimeException("Failed to verify index mapping", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPropertiesFromMapping(Map<String, Object> mapping) {
        return (Map<String, Object>) ((Map<String, Object>) mapping.get("properties"));
    }

    private void verifyField(Map<String, Object> properties, String fieldName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> field = (Map<String, Object>) properties.get(fieldName);
        if (field != null) {
            log.info("Field '{}' configuration:", fieldName);
            log.info("Type: {}", field.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> fields = (Map<String, Object>) field.get("fields");
            if (fields != null) {
                log.info("Multi-fields:");
                fields.forEach((subFieldName, config) -> {
                    log.info("  - {}: {}", subFieldName, config);
                });
            }
        } else {
            log.warn("Field '{}' not found in mapping", fieldName);
        }
    }

    public void verifyDocument(String id) {
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q
                            .term(t -> t
                                    .field("_id")
                                    .value(id)))
                    .build();

            SearchHits<DocumentIndex> searchHits = elasticsearchOperations.search(query, DocumentIndex.class);
            if (!searchHits.isEmpty()) {
                DocumentIndex doc = searchHits.getSearchHit(0).getContent();
                log.info("Document found: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc));

                // Test all field variations
                testFieldAccess(id, "filename");
                testFieldAccess(id, "filename.raw");
                testFieldAccess(id, "filename.analyzed");
                testFieldAccess(id, "filename.search");
                testFieldAccess(id, "content");
                testFieldAccess(id, "content.keyword");
                testFieldAccess(id, "content.analyzed");
            } else {
                log.info("No document found with id: {}", id);
            }
        } catch (Exception e) {
            log.error("Error verifying document", e);
        }
    }

    private void testFieldAccess(String documentId, String fieldPath) {
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q
                            .bool(b -> b
                                    .must(m -> m
                                            .term(t -> t
                                                    .field("_id")
                                                    .value(documentId)))
                                    .must(m -> m
                                            .exists(e -> e
                                                    .field(fieldPath)))))
                    .build();

            SearchHits<DocumentIndex> hits = elasticsearchOperations.search(query, DocumentIndex.class);
            log.info("Field '{}' exists and is accessible: {}", fieldPath, !hits.isEmpty());
        } catch (Exception e) {
            log.warn("Error testing field access for '{}': {}", fieldPath, e.getMessage());
        }
    }

    public void reindexAll() {
        try {
            log.info("Starting reindex operation...");
            elasticsearchOperations.indexOps(DocumentIndex.class).delete();
            log.info("Index deleted");

            elasticsearchOperations.indexOps(DocumentIndex.class).create();
            log.info("Index recreated");

            verifyIndexMapping();
            log.info("Reindex completed successfully");
        } catch (Exception e) {
            log.error("Error during reindex", e);
            throw new RuntimeException("Failed to reindex", e);
        }
    }
}