package com.dms.processor.repository;

import com.dms.processor.model.DocumentContent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DocumentContentRepository extends MongoRepository<DocumentContent, String> {
    Optional<DocumentContent> findByDocumentIdAndVersionNumber(String documentId, Integer versionNumber);
}