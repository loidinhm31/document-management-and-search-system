package com.dms.document.interaction.repository;

import com.dms.document.interaction.model.DocumentPreferences;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentPreferencesRepository extends MongoRepository<DocumentPreferences, String> {
    Optional<DocumentPreferences> findByUserId(String userId);

    @Query(value = "{'preferredMajors': ?0}", exists = true)
    boolean existsByPreferredMajor(String code);

    @Query(value = "{'preferredCourseCodes': ?0}", exists = true)
    boolean existsByPreferredCourseCode(String code);

    @Query(value = "{'preferredLevels': ?0}", exists = true)
    boolean existsByPreferredLevel(String code);

    @Query(value = "{'preferredCategories': ?0}", exists = true)
    boolean existsByPreferredCategory(String code);

    @Query(value = "{'preferredTags': ?0}", exists = true)
    boolean existsByPreferredTag(String code);
}
