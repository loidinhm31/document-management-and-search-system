package com.dms.document.service;

import com.dms.document.client.UserClient;
import com.dms.document.dto.DocumentContent;
import com.dms.document.dto.DocumentUpdateRequest;
import com.dms.document.dto.UserDto;
import com.dms.document.enums.DocumentType;
import com.dms.document.enums.EventType;
import com.dms.document.exception.InvalidDocumentException;
import com.dms.document.exception.UnsupportedDocumentTypeException;
import com.dms.document.model.DocumentInformation;
import com.dms.document.model.SyncEventRequest;
import com.dms.document.repository.DocumentRepository;
import com.dms.document.utils.DocumentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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
    private final ContentExtractorService contentExtractorService;

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

        // Extract content and metadata
        DocumentContent extractedContent = contentExtractorService.extractContent(fullPath);

        // Create document
        DocumentInformation document = DocumentInformation.builder()
                .filename(uniqueFilename)
                .originalFilename(originalFilename)
                .filePath(relativePath)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .documentType(DocumentUtils.determineDocumentType(file.getContentType()))
                .content(extractedContent.content())
                .major(major)
                .courseCode(courseCode)
                .courseLevel(level)
                .category(category)
                .tags(tags != null ? tags : new HashSet<>())
                .extractedMetadata(extractedContent.metadata())
                .userId(userDto.getUserId().toString())
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

    public Page<DocumentInformation> getUserDocuments(String username, int page, int size) {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        // Create query criteria for counting total elements
        Query countQuery = new Query(Criteria.where("userId").is(userDto.getUserId().toString())
                .and("deleted").ne(true));

        // Get total count of documents that match the criteria
        long total = mongoTemplate.count(countQuery, DocumentInformation.class);

        // Calculate correct skip value based on page and size
        int skip = page * size;

        // Create pageable request with sorting
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Create query for fetching the actual page of documents
        Query query = new Query(Criteria.where("userId").is(userDto.getUserId().toString())
                .and("deleted").ne(true))
                .with(pageable)
                .skip(skip)
                .limit(size);

        // Get documents for current page
        List<DocumentInformation> documents = mongoTemplate.find(query, DocumentInformation.class);

        // Return PageImpl with correct total count
        return new PageImpl<>(documents, pageable, total);
    }

    public byte[] getDocumentThumbnail(String documentId, String username) throws IOException {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        Path filePath = Path.of(storageBasePath, document.getFilePath());
        return thumbnailService.generateThumbnail(filePath, document.getDocumentType().name());
    }

    public byte[] getDocumentContent(String documentId, String username) throws IOException {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDataAccessResourceUsageException("Document not found"));

        Path filePath = Path.of(storageBasePath, document.getFilePath());
        try (InputStream in = Files.newInputStream(filePath)) {
            return in.readAllBytes();
        }
    }

    public DocumentInformation getDocumentDetails(String documentId, String username) {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        return documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));
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
            Path filePath = Path.of(storageBasePath, document.getFilePath());
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

    public DocumentInformation updateDocumentFile(String documentId, MultipartFile file, String username) throws IOException {
        // Verify user access
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        // Get existing document
        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Validate new file
        validateDocument(file);

        // Delete old file
        try {
            Path oldFilePath = Path.of(storageBasePath, document.getFilePath());
            Files.deleteIfExists(oldFilePath);
        } catch (IOException e) {
            log.error("Error deleting old file for document: {}", documentId, e);
        }

        // Generate new filename and save new file
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String uniqueFilename = UUID.randomUUID() + fileExtension;
        String relativePath = createStoragePath(uniqueFilename);
        Path fullPath = Path.of(storageBasePath, relativePath);

        // Create directories if they don't exist
        Files.createDirectories(fullPath.getParent());

        // Save new file to disk
        Files.copy(file.getInputStream(), fullPath, StandardCopyOption.REPLACE_EXISTING);

        // Extract content and metadata
        DocumentContent extractedContent = contentExtractorService.extractContent(fullPath);

        // Update document information
        document.setFilename(uniqueFilename);
        document.setOriginalFilename(file.getOriginalFilename());
        document.setFilePath(relativePath);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDocumentType(DocumentUtils.determineDocumentType(file.getContentType()));
        document.setContent(extractedContent.content());
        document.setExtractedMetadata(extractedContent.metadata());
        document.setUpdatedAt(new Date());
        document.setUpdatedBy(username);

        // Save updated document
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

    public Set<String> getPopularTags(String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            return new HashSet<>(documentRepository.findDistinctTagsByPattern(prefix));
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
