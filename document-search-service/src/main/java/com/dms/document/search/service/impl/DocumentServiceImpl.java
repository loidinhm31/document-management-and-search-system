package com.dms.document.search.service.impl;

import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.DocumentSearchCriteria;
import com.dms.document.search.dto.UserResponse;
import com.dms.document.search.enums.AppRole;
import com.dms.document.search.enums.DocumentReportStatus;
import com.dms.document.search.enums.SharingType;
import com.dms.document.search.model.DocumentInformation;
import com.dms.document.search.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {
    private final MongoTemplate mongoTemplate;
    private final UserClient userClient;

    public Page<DocumentInformation> getUserDocuments(String username, DocumentSearchCriteria criteria, int page, int size) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        if (!(Objects.equals(userResponse.role().roleName(), AppRole.ROLE_USER) ||
              Objects.equals(userResponse.role().roleName(), AppRole.ROLE_MENTOR))) {
            throw new InvalidDataAccessResourceUsageException("Invalid role");
        }

        // Create a list to hold all criteria
        List<Criteria> criteriaList = new ArrayList<>();

        // Basic criteria for non-deleted documents
        criteriaList.add(Criteria.where("deleted").ne(true));

        // Access criteria for ownership and sharing
        Criteria ownedCriteria = Criteria.where("userId").is(userResponse.userId().toString());

        Criteria sharedCriteria = Criteria.where("sharedWith").in(userResponse.userId().toString());

        criteriaList.add(new Criteria().orOperator(ownedCriteria, sharedCriteria));

        // Violation criteria
        Criteria violatedCriteria = Criteria.where("reportStatus").ne(DocumentReportStatus.RESOLVED.name());
        criteriaList.add(violatedCriteria);

        // Add search criteria if provided
        if (StringUtils.isNotBlank(criteria.getSearch())) {
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("filename").regex(criteria.getSearch(), "i"),
                    Criteria.where("content").regex(criteria.getSearch(), "i")
            );
            criteriaList.add(searchCriteria);
        }

        // Add filters if provided
        if (CollectionUtils.isNotEmpty(criteria.getMajors())) {
            criteriaList.add(Criteria.where("majors").in(criteria.getMajors()));
        }
        if (CollectionUtils.isNotEmpty(criteria.getCourseCodes())) {
            criteriaList.add(Criteria.where("course_codes").in(criteria.getCourseCodes()));
        }
        if (StringUtils.isNotBlank(criteria.getLevel())) {
            criteriaList.add(Criteria.where("courseLevel").is(criteria.getLevel()));
        }
        if (CollectionUtils.isNotEmpty(criteria.getCategories())) {
            criteriaList.add(Criteria.where("categories").in(criteria.getCategories()));
        }

        // Add tag filter if provided
        if (CollectionUtils.isNotEmpty(criteria.getTags())) {
            criteriaList.add(Criteria.where("tags").in(criteria.getTags()));
        }

        // Combine all criteria with AND
        Criteria finalCriteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));

        // Create pageable with sort
        Sort sort = Sort.by(Sort.Direction.fromString(criteria.getSortDirection()), criteria.getSortField());
        Pageable pageable = PageRequest.of(page, size, sort);

        // Execute query
        Query countQuery = new Query(finalCriteria);
        long total = mongoTemplate.count(countQuery, DocumentInformation.class);

        Query query = new Query(finalCriteria)
                .with(pageable);
        query.fields().exclude("content"); // Exclude the content field from the query results
        List<DocumentInformation> documents = mongoTemplate.find(query, DocumentInformation.class);

        return new PageImpl<>(documents, pageable, total);
    }
}