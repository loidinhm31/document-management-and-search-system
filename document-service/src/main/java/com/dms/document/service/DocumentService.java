package com.dms.document.service;

import com.dms.document.client.UserClient;
import com.dms.document.dto.DocumentUpdateRequest;
import com.dms.document.dto.ShareSettings;
import com.dms.document.dto.UpdateShareSettingsRequest;
import com.dms.document.dto.UserDto;
import com.dms.document.enums.DocumentStatus;
import com.dms.document.enums.DocumentType;
import com.dms.document.enums.EventType;
import com.dms.document.enums.SharingType;
import com.dms.document.exception.InvalidDocumentException;
import com.dms.document.exception.UnsupportedDocumentTypeException;
import com.dms.document.model.DocumentInformation;
import com.dms.document.dto.DocumentSearchCriteria;
import com.dms.document.dto.SyncEventRequest;
import com.dms.document.repository.DocumentRepository;
import com.dms.document.utils.DocumentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final PublishEventService publishEventService;
    @Value("${app.document.storage.path}")
    private String storageBasePath;

    @Value("${app.document.max-size-mb}")
    private DataSize maxFileSize;

    private final MongoTemplate mongoTemplate;

    private final DocumentRepository documentRepository;
    private final UserClient userClient;
    private final ThumbnailService thumbnailService;

    public DocumentInformation uploadDocument(MultipartFile file,
                                              String courseCode,
                                              String major,
                                              String level,
                                              String category,
                                              Set<String> tags,
                                              String username) throws IOException {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }

        UserDto userDto = response.getBody();

        validateDocument(file);

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID() + fileExtension;

        // Create storage path
        String relativePath = createStoragePath(uniqueFilename);
        Path fullPath = Path.of(storageBasePath, relativePath);

        // Create directories if they don't exist
        Files.createDirectories(fullPath.getParent());

        // Save file to disk
        Files.copy(file.getInputStream(), fullPath, StandardCopyOption.REPLACE_EXISTING);

        // Create document
        DocumentInformation document = DocumentInformation.builder()
                .status(DocumentStatus.PENDING)
                .filename(uniqueFilename)
                .originalFilename(originalFilename)
                .filePath(fullPath.toString())
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .documentType(DocumentUtils.determineDocumentType(file.getContentType()))
                .major(major)
                .courseCode(courseCode)
                .courseLevel(level)
                .category(category)
                .tags(tags != null ? tags : new HashSet<>())
                .userId(userDto.getUserId().toString())
                .deleted(false)
                .createdAt(new Date())
                .createdBy(username)
                .updatedAt(new Date())
                .updatedBy(username)
                .build();

        // Save to MongoDB
        DocumentInformation savedDocument = documentRepository.save(document);

        log.info("Saved document: {}", savedDocument.getFilename());
        // Send sync event
        CompletableFuture.runAsync(() -> publishEventService.sendSyncEvent(
                SyncEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .userId(userDto.getUserId().toString())
                        .documentId(savedDocument.getId())
                        .subject(EventType.SYNC_EVENT.name())
                        .triggerAt(LocalDateTime.now())
                        .build()
        ));

        return savedDocument;
    }

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

    public byte[] getDocumentThumbnail(String documentId, String username) throws IOException {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        return thumbnailService.generateThumbnail(Path.of(document.getFilePath()), document.getDocumentType(), document.getContent());
    }

    public byte[] getDocumentContent(String documentId, String username) throws IOException {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDataAccessResourceUsageException("Document not found"));

        try (InputStream in = Files.newInputStream(Path.of(document.getFilePath()))) {
            return in.readAllBytes();
        }
    }

    public DocumentInformation getDocumentDetails(String documentId, String username) {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        DocumentInformation documentInformation = documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));
        documentInformation.setContent(null);
        return documentInformation;
    }

    public DocumentInformation updateDocument(
            String documentId,
            DocumentUpdateRequest documentUpdateRequest,
            String username) {

        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Update fields if provided
        if (Objects.nonNull(document)) {
            document.setCourseCode(documentUpdateRequest.courseCode());
            document.setMajor(documentUpdateRequest.major());
            document.setCourseLevel(documentUpdateRequest.level());
            document.setCategory(documentUpdateRequest.category());
            document.setTags(documentUpdateRequest.tags());
        }
        document.setUpdatedAt(new Date());
        document.setUpdatedBy(username);

        DocumentInformation updatedDocument = documentRepository.save(document);

        // Send sync event for elasticsearch update
        CompletableFuture.runAsync(() -> publishEventService.sendSyncEvent(
                SyncEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .userId(userDto.getUserId().toString())
                        .documentId(documentId)
                        .subject(EventType.UPDATE_EVENT.name())
                        .triggerAt(LocalDateTime.now())
                        .build()
        ));

        return updatedDocument;
    }

    @Transactional
    public DocumentInformation updateDocumentWithFile(
            String documentId,
            MultipartFile file,
            DocumentUpdateRequest metadata,
            String username) throws IOException {

        // Get existing document
        DocumentInformation document = getDocumentDetails(documentId, username);

        // Delete old file
        try {
            Path oldFilePath = Path.of(document.getFilePath());
            Files.deleteIfExists(oldFilePath);
        } catch (IOException e) {
            log.error("Error deleting old file for document: {}", documentId, e);
        }

        // Save new file
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String uniqueFilename = UUID.randomUUID() + fileExtension;
        String relativePath = createStoragePath(uniqueFilename);
        Path fullPath = Path.of(storageBasePath, relativePath);

        // Create directories if they don't exist
        Files.createDirectories(fullPath.getParent());

        // Save new file to disk
        Files.copy(file.getInputStream(), fullPath, StandardCopyOption.REPLACE_EXISTING);

        // Update document information
        document.setFilename(uniqueFilename);
        document.setOriginalFilename(file.getOriginalFilename());
        document.setFilePath(fullPath.toString());
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDocumentType(DocumentUtils.determineDocumentType(file.getContentType()));

        // Update metadata
        document.setCourseCode(metadata.courseCode());
        document.setMajor(metadata.major());
        document.setCourseLevel(metadata.level());
        document.setCategory(metadata.category());
        document.setTags(metadata.tags());

        document.setUpdatedAt(new Date());
        document.setUpdatedBy(username);

        // Save all changes in one transaction
        DocumentInformation updatedDocument = documentRepository.save(document);

        // Send single sync event for both file and metadata update
        CompletableFuture.runAsync(() -> publishEventService.sendSyncEvent(
                SyncEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .userId(document.getUserId())
                        .documentId(documentId)
                        .subject(EventType.UPDATE_EVENT_WITH_FILE.name())
                        .triggerAt(LocalDateTime.now())
                        .build()
        ));

        return updatedDocument;
    }

    public void deleteDocument(String documentId, String username) {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Delete the physical file
        try {
            Path filePath = Path.of(document.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Error deleting file for document: {}", documentId, e);
        }

        // Soft delete in database
        document.setDeleted(true);
        document.setUpdatedAt(new Date());
        document.setUpdatedBy(username);
        documentRepository.save(document);

        // Send sync event for elasticsearch deletion
        CompletableFuture.runAsync(() -> publishEventService.sendSyncEvent(
                SyncEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .userId(userDto.getUserId().toString())
                        .documentId(documentId)
                        .subject(EventType.DELETE_EVENT.name())
                        .triggerAt(LocalDateTime.now())
                        .build()
        ));
    }

    public ShareSettings getShareSettings(String documentId, String username) {
        DocumentInformation doc = getDocumentDetails(documentId, username);
        return new ShareSettings(
                doc.getSharingType() == SharingType.PUBLIC,
                doc.getSharedWith()
        );
    }

    public DocumentInformation updateShareSettings(
            String documentId,
            UpdateShareSettingsRequest request,
            String username) {

        DocumentInformation doc = getDocumentDetails(documentId, username);

        doc.setSharingType(request.isPublic() ? SharingType.PUBLIC :
                CollectionUtils.isEmpty(request.sharedWith()) ? SharingType.PRIVATE : SharingType.SPECIFIC);
        doc.setSharedWith(request.sharedWith());
        doc.setUpdatedAt(new Date());
        doc.setUpdatedBy(username);

        // Send sync event to update Elasticsearch
        CompletableFuture.runAsync(() -> publishEventService.sendSyncEvent(
                SyncEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .userId(doc.getUserId())
                        .documentId(documentId)
                        .subject(EventType.UPDATE_EVENT.name())
                        .triggerAt(LocalDateTime.now())
                        .build()
        ));

        return documentRepository.save(doc);
    }

    public Set<String> getPopularTags(String prefix) {
        if (StringUtils.isNotEmpty(prefix)) {
            return documentRepository.findDistinctTagsByPattern(prefix).stream()
                    .flatMap(doc -> doc.getTags().stream())
                    .collect(Collectors.toSet());
        }

        // If no prefix, get all unique tags
        return documentRepository.findAllTags().stream()
                .flatMap(doc -> doc.getTags().stream())
                .collect(Collectors.toSet());
    }

    private void validateDocument(MultipartFile file) {
        // Check if file is empty
        if (file.isEmpty()) {
            throw new InvalidDocumentException("File is empty");
        }

        // Check file size (configurable)
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new InvalidDocumentException("File size exceeds maximum limit of " + maxFileSize + " bytes");
        }

        // Check MIME type
        String mimeType = file.getContentType();
        if (mimeType == null || !DocumentType.isSupportedMimeType(mimeType)) {
            throw new UnsupportedDocumentTypeException("Unsupported document type: " + mimeType);
        }

        // Additional validation: check file extension matches MIME type
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = getFileExtension(originalFilename).toLowerCase();
            boolean isValidExtension = switch (mimeType) {
                case "application/pdf" -> extension.equals(".pdf");
                case "application/msword" -> extension.equals(".doc");
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        extension.equals(".docx");
                case "application/vnd.ms-excel" -> extension.equals(".xls");
                case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> extension.equals(".xlsx");
                case "application/vnd.ms-powerpoint" -> extension.equals(".ppt");
                case "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                        extension.equals(".pptx");
                case "text/plain" -> extension.equals(".txt");
                case "application/rtf" -> extension.equals(".rtf");
                case "text/csv" -> extension.equals(".csv");
                case "application/xml" -> extension.equals(".xml");
                case "application/json" -> extension.equals(".json");
                default -> false;
            };

            if (!isValidExtension) {
                throw new InvalidDocumentException("File extension does not match the content type");
            }
        }
    }

    private String createStoragePath(String filename) {
        // Create path structure: yyyy-MM-dd/filename
        LocalDate now = LocalDate.now();
        return String.format("%d-%02d-%02d/%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), filename);
    }

    private String getFileExtension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(f.lastIndexOf(".")))
                .orElse("");
    }

}
