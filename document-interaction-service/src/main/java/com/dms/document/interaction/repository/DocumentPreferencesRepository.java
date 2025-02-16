package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentPreferences;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentPreferencesRepository extends MongoRepository<DocumentPreferences, String> {
    Optional<DocumentPreferences> findByUserId(String userId);
}
