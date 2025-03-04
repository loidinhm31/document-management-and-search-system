package com.dms.processor.repository;

import com.dms.processor.model.DocumentInformation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DocumentRepository extends MongoRepository<DocumentInformation, String> {
}