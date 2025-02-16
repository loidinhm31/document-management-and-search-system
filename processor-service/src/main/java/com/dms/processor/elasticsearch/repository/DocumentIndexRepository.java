package com.dms.processor.elasticsearch.repository;

import com.dms.processor.elasticsearch.DocumentIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class DocumentIndexRepository {
    private static final String INDEX_NAME = "documents";

    private final RestHighLevelClient client;
    private final ObjectMapper objectMapper;

    public DocumentIndexRepository(RestHighLevelClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public void save(DocumentIndex document) {
        try {
            String jsonDocument = objectMapper.writeValueAsString(document);
            IndexRequest indexRequest = new IndexRequest(INDEX_NAME)
                    .id(document.getId())
                    .source(jsonDocument, XContentType.JSON);

            client.index(indexRequest, RequestOptions.DEFAULT);
            log.info("Document indexed successfully: {}", document.getId());
        } catch (IOException e) {
            log.error("Error indexing document: {}", document.getId(), e);
            throw new RuntimeException("Failed to index document", e);
        }
    }

    public Optional<DocumentIndex> findById(String id) {
        try {
            GetRequest getRequest = new GetRequest(INDEX_NAME, id);
            GetResponse response = client.get(getRequest, RequestOptions.DEFAULT);

            if (response.isExists()) {
                DocumentIndex document = objectMapper.readValue(response.getSourceAsString(), DocumentIndex.class);
                return Optional.of(document);
            }
            return Optional.empty();
        } catch (IOException e) {
            log.error("Error retrieving document: {}", id, e);
            throw new RuntimeException("Failed to retrieve document", e);
        }
    }

    public void deleteById(String id) {
        try {
            DeleteRequest deleteRequest = new DeleteRequest(INDEX_NAME, id);
            client.delete(deleteRequest, RequestOptions.DEFAULT);
            log.info("Document deleted successfully: {}", id);
        } catch (IOException e) {
            log.error("Error deleting document: {}", id, e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    public List<DocumentIndex> findAll() {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchAllQuery());
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
            List<DocumentIndex> documents = new ArrayList<>();

            for (SearchHit hit : response.getHits().getHits()) {
                DocumentIndex document = objectMapper.readValue(hit.getSourceAsString(), DocumentIndex.class);
                documents.add(document);
            }

            return documents;
        } catch (IOException e) {
            log.error("Error retrieving all documents", e);
            throw new RuntimeException("Failed to retrieve documents", e);
        }
    }

    public List<DocumentIndex> findByUserId(String userId) {
        try {
            SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.termQuery("userId", userId));
            searchRequest.source(searchSourceBuilder);

            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
            List<DocumentIndex> documents = new ArrayList<>();

            for (SearchHit hit : response.getHits().getHits()) {
                DocumentIndex document = objectMapper.readValue(hit.getSourceAsString(), DocumentIndex.class);
                documents.add(document);
            }

            return documents;
        } catch (IOException e) {
            log.error("Error retrieving documents for user: {}", userId, e);
            throw new RuntimeException("Failed to retrieve user documents", e);
        }
    }
}