package com.dms.document.interaction.repository;


import com.dms.document.interaction.dto.TagsResponse;
import com.dms.document.interaction.model.DocumentInformation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends MongoRepository<DocumentInformation, String> {
    Optional<DocumentInformation> findByIdAndUserId(String id, String userId);

    List<DocumentInformation> findByIdIn(List<String> ids);

    @Query(value = "{'tags': {'$regex': ?0, '$options': 'i'}, 'deleted': {'$ne': true}}", fields = "{'_id': 0, 'tags': 1}")
    List<TagsResponse> findDistinctTagsByPattern(String pattern);

    @Query(value = "{'tags': {'$exists': true}, 'deleted': {'$ne': true}}", fields = "{'_id': 0, 'tags': 1}")
    List<TagsResponse> findAllTags();

    @Query("""
            { '_id': ?0, '$or': [
            { 'user_id': ?1 },
            { 'sharing_type': 'PUBLIC' },
            { '$and': [
                { 'sharing_type': 'SPECIFIC' },
                { 'shared_with': ?1 }
              ] }
            ],
            'deleted': {'$ne': true},
            'report_status': {$ne: 'RESOLVED'}
            }
            """)
    Optional<DocumentInformation> findAccessibleDocumentByIdAndUserId(String id, String userId);

    @Query(value = "{'majors': ?0, 'deleted': {$ne: true}}", exists = true)
    boolean existsByMajorCode(String code);

    @Query(value = "{'courseCodes': ?0, 'deleted': {$ne: true}}", exists = true)
    boolean existsByCourseCode(String code);

    @Query(value = "{'courseLevel': ?0, 'deleted': {$ne: true}}", exists = true)
    boolean existsByCourseLevelCode(String code);

    @Query(value = "{'categories': ?0, 'deleted': {$ne: true}}", exists = true)
    boolean existsByCategoryCode(String code);
}