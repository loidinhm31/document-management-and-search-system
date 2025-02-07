package com.dms.document.search.repository;

import com.dms.document.search.model.DocumentPreferences;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentPreferencesRepository extends MongoRepository<DocumentPreferences, String> {
    Optional<DocumentPreferences> findByUserId(String userId);
}
