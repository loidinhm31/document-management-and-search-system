package com.dms.search.elasticsearch.repository;

import com.dms.search.elasticsearch.DocumentIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface DocumentIndexRepository extends ElasticsearchRepository<DocumentIndex, String> {
}
