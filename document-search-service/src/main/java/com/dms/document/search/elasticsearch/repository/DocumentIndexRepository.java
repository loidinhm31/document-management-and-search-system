package com.dms.document.search.elasticsearch.repository;

import com.dms.document.search.elasticsearch.DocumentIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface DocumentIndexRepository extends ElasticsearchRepository<DocumentIndex, String> {
}
