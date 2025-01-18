package com.sdms.document.repository;

import com.sdms.document.model.DocumentInformation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends MongoRepository<DocumentInformation, String> {
    Optional<DocumentInformation> findByIdAndUserId(String id, String userId);

    @Query("{'tags': {'$regex': ?0, '$options': 'i'}}")
    List<String> findDistinctTagsByPattern(String pattern);

    @Query(value = "{'tags': {'$exists': true}}", fields = "{'tags': 1}")
    List<DocumentInformation> findAllTags();
}