package com.dms.processor.elasticsearch.repository;

import com.dms.processor.elasticsearch.DocumentIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface DocumentIndexRepository extends ElasticsearchRepository<DocumentIndex, String> {
}
