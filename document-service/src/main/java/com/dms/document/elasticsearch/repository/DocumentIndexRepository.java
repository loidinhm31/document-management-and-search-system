package com.dms.document.elasticsearch.repository;

import com.dms.document.elasticsearch.DocumentIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface DocumentIndexRepository extends ElasticsearchRepository<DocumentIndex, String> {
}
