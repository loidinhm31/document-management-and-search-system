package com.dms.document.search.repository;


import com.dms.document.search.model.DocumentInformation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface DocumentRepository extends MongoRepository<DocumentInformation, String> {

    @Query("{ '_id': ?0, '$or': [ " +
            "{ 'user_id': ?1 }, " +
            "{ 'sharing_type': 'PUBLIC' }, " +
            "{ '$and': [ " +
            "    { 'sharing_type': 'SPECIFIC' }, " +
            "    { 'shared_with': ?1 } " +
            "  ] } " +
            "] }")
    Optional<DocumentInformation> findAccessibleDocumentByIdAndUserId(String id, String userId);
}