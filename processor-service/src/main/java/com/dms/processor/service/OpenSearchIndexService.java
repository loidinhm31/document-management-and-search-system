package com.dms.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.indices.GetMappingsRequest;
import org.opensearch.client.indices.GetMappingsResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenSearchIndexService {
    private final RestHighLevelClient openSearchClient;
    private final ObjectMapper objectMapper;
    private static final String INDEX_NAME = "documents";

    @PostConstruct
    public void setupIndex() {
        try {
            // Check if index exists
            boolean indexExists = openSearchClient.indices()
                    .exists(new GetIndexRequest(INDEX_NAME), RequestOptions.DEFAULT);

            if (indexExists) {
                log.info("Index exists. Verifying mapping...");
                verifyIndexMapping();
            } else {
                log.info("Creating index with mapping and settings...");
                CreateIndexRequest request = new CreateIndexRequest(INDEX_NAME);

                // Load settings and mappings from resources
                String settings = loadResourceFile("/opensearch/settings.json");
                String mappings = loadResourceFile("/opensearch/mappings.json");

                request.settings(settings, XContentType.JSON);
                request.mapping(mappings, XContentType.JSON);

                openSearchClient.indices().create(request, RequestOptions.DEFAULT);
                log.info("Index created successfully");

                // Verify the newly created mapping
                verifyIndexMapping();
            }
        } catch (Exception e) {
            log.error("Error setting up OpenSearch index", e);
            throw new RuntimeException("Failed to setup OpenSearch index", e);
        }
    }

    public void verifyIndexMapping() {
        try {
            GetMappingsRequest request = new GetMappingsRequest().indices(INDEX_NAME);
            GetMappingsResponse getMappingResponse = openSearchClient.indices().getMapping(request, RequestOptions.DEFAULT);
            MappingMetadata mappingMetadata = getMappingResponse.mappings().get(INDEX_NAME);

            if (mappingMetadata != null) {
                Map<String, Object> mapping = mappingMetadata.sourceAsMap();
                String mappingJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapping);
                log.info("Current mapping: {}", mappingJson);

                // Verify field mappings
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) mapping.get("properties");
                verifyField(properties, "filename");
                verifyField(properties, "content");
                verifyField(properties, "major");
                verifyField(properties, "course_code");
                verifyField(properties, "tags");
            } else {
                log.warn("No mapping found for index: {}", INDEX_NAME);
            }
        } catch (Exception e) {
            log.error("Error verifying index mapping", e);
            throw new RuntimeException("Failed to verify index mapping", e);
        }
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
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery("_id", id));
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);
            if (Objects.requireNonNull(response.getHits().getTotalHits()).value > 0) {
                String docJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(response.getHits().getHits()[0].getSourceAsMap());
                log.info("Document found: {}", docJson);

                // Test all field variations
                testFieldAccess(id, "filename");
                testFieldAccess(id, "filename.raw");
                testFieldAccess(id, "filename.analyzed");
                testFieldAccess(id, "filename.search");
                testFieldAccess(id, "content");
                testFieldAccess(id, "content.keyword");
            } else {
                log.info("No document found with id: {}", id);
            }
        } catch (Exception e) {
            log.error("Error verifying document", e);
        }
    }

    private void testFieldAccess(String documentId, String fieldPath) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(
                    QueryBuilders.boolQuery()
                            .must(QueryBuilders.termQuery("_id", documentId))
                            .must(QueryBuilders.existsQuery(fieldPath))
            );
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = openSearchClient.search(searchRequest, RequestOptions.DEFAULT);
            log.info("Field '{}' exists and is accessible: {}", fieldPath, response.getHits().getTotalHits().value > 0);
        } catch (Exception e) {
            log.warn("Error testing field access for '{}': {}", fieldPath, e.getMessage());
        }
    }

    public void reindexAll() {
        try {
            log.info("Starting reindex operation...");

            // Delete existing index
            DeleteIndexRequest deleteRequest = new DeleteIndexRequest(INDEX_NAME);
            openSearchClient.indices().delete(deleteRequest, RequestOptions.DEFAULT);
            log.info("Index deleted");

            // Recreate index
            setupIndex();
            log.info("Index recreated successfully");

        } catch (Exception e) {
            log.error("Error during reindex", e);
            throw new RuntimeException("Failed to reindex", e);
        }
    }

    private String loadResourceFile(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}