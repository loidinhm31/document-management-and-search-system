package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentContent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DocumentContentRepository extends MongoRepository<DocumentContent, String> {
}