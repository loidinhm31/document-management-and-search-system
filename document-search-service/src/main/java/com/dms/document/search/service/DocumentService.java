package com.dms.document.search.service;

import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.DocumentSearchCriteria;
import com.dms.document.search.dto.UserDto;
import com.dms.document.search.exception.InvalidDocumentException;
import com.dms.document.search.model.DocumentInformation;
import com.dms.document.search.repository.DocumentRepository;
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

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final MongoTemplate mongoTemplate;
    private final UserClient userClient;
    private final DocumentRepository documentRepository;
    private final DiscoverDocumentSearchService discoverDocumentSearchService;

    public Page<DocumentInformation> getUserDocuments(String username, DocumentSearchCriteria criteria, int page, int size) {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        // Build the query criteria
        Criteria queryCriteria = Criteria.where("userId").is(userDto.getUserId().toString())
                .and("deleted").ne(true);

        // Add search criteria if provided
        if (StringUtils.isNotBlank(criteria.getSearch())) {
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("originalFilename").regex(criteria.getSearch(), "i"),
                    Criteria.where("content").regex(criteria.getSearch(), "i"),
                    Criteria.where("courseCode").regex(criteria.getSearch(), "i"),
                    Criteria.where("tags").regex(criteria.getSearch(), "i")
            );
            queryCriteria.andOperator(searchCriteria);
        }

        // Add filters if provided
        if (StringUtils.isNotBlank(criteria.getMajor())) {
            queryCriteria.and("major").is(criteria.getMajor());
        }
        if (StringUtils.isNotBlank(criteria.getLevel())) {
            queryCriteria.and("courseLevel").is(criteria.getLevel());
        }
        if (StringUtils.isNotBlank(criteria.getCategory())) {
            queryCriteria.and("category").is(criteria.getCategory());
        }

        // Add tag filter if provided
        if (CollectionUtils.isNotEmpty(criteria.getTags())) {
            queryCriteria.and("tags").all(criteria.getTags());
        }

        // Create pageable with sort
        Sort sort = Sort.by(Sort.Direction.fromString(criteria.getSortDirection()), criteria.getSortField());
        Pageable pageable = PageRequest.of(page, size, sort);

        // Execute query
        Query query = new Query(queryCriteria).with(pageable);
        long total = mongoTemplate.count(query, DocumentInformation.class);
        List<DocumentInformation> documents = mongoTemplate.find(query, DocumentInformation.class);

        return new PageImpl<>(
                documents.stream()
                        .peek(d -> d.setContent(null))
                        .toList(),
                pageable,
                total
        );
    }

    public Page<DocumentResponseDto> getRelatedDocuments(String documentId, String username, Pageable pageable) {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        // First check if user has access to the source document
        DocumentInformation sourceDoc = documentRepository.findAccessibleDocumentByIdAndUserId(
                documentId,
                userDto.getUserId().toString()
        ).orElseThrow(() -> new InvalidDocumentException("Document not found"));

        return discoverDocumentSearchService.findRelatedDocuments(
                documentId,
                userDto.getUserId().toString(),
                pageable
        );
    }
}
