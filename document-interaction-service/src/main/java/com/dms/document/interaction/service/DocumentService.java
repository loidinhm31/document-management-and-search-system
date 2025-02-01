package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.DocumentStatus;
import com.dms.document.interaction.enums.DocumentType;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.SharingType;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.exception.UnsupportedDocumentTypeException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentVersion;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.utils.DocumentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    @Value("${app.document.storage.path}")
    private String storageBasePath;

    @Value("${app.document.max-size-mb}")
    private DataSize maxFileSize;

    @Value("${app.document.placeholder.processing}")
    private Resource processingPlaceholder;

    @Value("${app.document.placeholder.error}")
    private Resource errorPlaceholder;

    private final PublishEventService publishEventService;
    private final DocumentRepository documentRepository;
    private final UserClient userClient;

    public DocumentInformation uploadDocument(MultipartFile file,
                                              String summary,
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
        String uniqueFilename = getFileName(originalFilename) + "_" + Instant.now().toEpochMilli() + fileExtension;

        // Create storage path
        String relativePath = createStoragePath(uniqueFilename);
        Path fullPath = Path.of(storageBasePath, relativePath);

        // Create directories if they don't exist
        Files.createDirectories(fullPath.getParent());

        // Save file to disk
        Files.copy(file.getInputStream(), fullPath, StandardCopyOption.REPLACE_EXISTING);

        // Create new version metadata
        int nextVersion = 0;
        DocumentVersion newVersion = DocumentVersion.builder()
                .versionNumber(nextVersion)
                .filePath(fullPath.toString())
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .status(DocumentStatus.PENDING)
                .createdBy(username)
                .createdAt(new Date())
                .build();

        // Create document with PENDING status
        DocumentInformation document = DocumentInformation.builder()
                .status(DocumentStatus.PENDING)
                .filename(originalFilename)
                .filePath(fullPath.toString())
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .documentType(DocumentUtils.determineDocumentType(file.getContentType()))
                .summary(summary)
                .major(major)
                .courseCode(courseCode)
                .courseLevel(level)
                .category(category)
                .tags(tags != null ? tags : new HashSet<>())
                .userId(userDto.getUserId().toString())
                .sharingType(SharingType.PRIVATE)
                .sharedWith(new HashSet<>())
                .deleted(false)
                .currentVersion(nextVersion)
                .versions(List.of(newVersion))
                .createdAt(new Date())
                .createdBy(username)
                .updatedAt(new Date())
                .updatedBy(username)
                .build();

        // Save data
        DocumentInformation savedDocument = documentRepository.save(document);
        log.info("Saved document: {}", savedDocument.getFilename());

        // Send sync event for processing
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

    public ThumbnailResponse getDocumentThumbnail(String documentId, String username) throws IOException {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // If document is still being processed, return processing placeholder
        // with a header indicating it's still processing
        if (document.getStatus() == DocumentStatus.PENDING ||
                document.getStatus() == DocumentStatus.PROCESSING) {
            return ThumbnailResponse.builder()
                    .data(getProcessingPlaceholder())
                    .status(HttpStatus.ACCEPTED)
                    .isPlaceholder(true)
                    .retryAfterSeconds(10) // Suggest client to retry after 10 seconds
                    .build();
        }

        // If document has failed processing, return error placeholder
        if (document.getStatus() == DocumentStatus.FAILED) {
            return ThumbnailResponse.builder()
                    .data(getErrorPlaceholder())
                    .status(HttpStatus.NOT_FOUND)
                    .isPlaceholder(true)
                    .build();
        }

        // If thumbnail path is not available, return error placeholder
        if (StringUtils.isEmpty(document.getThumbnailPath())) {
            log.warn("Thumbnail path not found for document: {}", documentId);
            return ThumbnailResponse.builder()
                    .data(getErrorPlaceholder())
                    .status(HttpStatus.NOT_FOUND)
                    .isPlaceholder(true)
                    .build();
        }

        try {
            // Try to read the thumbnail file
            Path thumbnailPath = Path.of(document.getThumbnailPath());
            if (Files.exists(thumbnailPath)) {
                byte[] thumbnailData = Files.readAllBytes(thumbnailPath);
                return ThumbnailResponse.builder()
                        .data(thumbnailData)
                        .status(HttpStatus.OK)
                        .isPlaceholder(false)
                        .build();
            } else {
                log.warn("Thumbnail file not found at path: {}", document.getThumbnailPath());
                return ThumbnailResponse.builder()
                        .data(getErrorPlaceholder())
                        .status(HttpStatus.NOT_FOUND)
                        .isPlaceholder(true)
                        .build();
            }
        } catch (IOException e) {
            log.error("Error reading thumbnail for document: {}", documentId, e);
            return ThumbnailResponse.builder()
                    .data(getErrorPlaceholder())
                    .status(HttpStatus.NOT_FOUND)
                    .isPlaceholder(true)
                    .build();
        }
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

        DocumentInformation documentInformation = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userDto.getUserId().toString())
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
            document.setSummary(documentUpdateRequest.summary());
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

    public DocumentInformation updateDocumentWithFile(
            String documentId,
            MultipartFile file,
            DocumentUpdateRequest documentUpdateRequest,
            String username) throws IOException {

        // Get existing document
        DocumentInformation document = getDocumentDetails(documentId, username);

        // Save new file
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = getFileName(originalFilename) + "_" + Instant.now().toEpochMilli() + fileExtension;
        String relativePath = createStoragePath(uniqueFilename);
        Path fullPath = Path.of(storageBasePath, relativePath);

        // Create directories if they don't exist
        Files.createDirectories(fullPath.getParent());

        // Save new file to disk
        Files.copy(file.getInputStream(), fullPath, StandardCopyOption.REPLACE_EXISTING);

        // Create new version metadata
        int nextVersion = (document.getCurrentVersion() != null ? document.getCurrentVersion() : 0) + 1;
        DocumentVersion newVersion = DocumentVersion.builder()
                .versionNumber(nextVersion)
                .filePath(fullPath.toString())
                .filename(originalFilename)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .status(DocumentStatus.PENDING)
                .createdBy(username)
                .createdAt(new Date())
                .build();

        // Update document information
        document.setStatus(DocumentStatus.PENDING); // Reset to pending for reprocessing
        document.setFilename(originalFilename);
        document.setFilePath(fullPath.toString());
        document.setThumbnailPath(null); // Reset thumbnail - will be generated for new version
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDocumentType(DocumentUtils.determineDocumentType(file.getContentType()));

        // Update metadata
        document.setSummary(documentUpdateRequest.summary());
        document.setCourseCode(documentUpdateRequest.courseCode());
        document.setMajor(documentUpdateRequest.major());
        document.setCourseLevel(documentUpdateRequest.level());
        document.setCategory(documentUpdateRequest.category());
        document.setTags(documentUpdateRequest.tags());
        document.setUpdatedAt(new Date());
        document.setUpdatedBy(username);

        document.setCurrentVersion(nextVersion);
        // Add new version to versions list
        List<DocumentVersion> versions = new ArrayList<>(
                CollectionUtils.isNotEmpty(document.getVersions()) ? document.getVersions() : CollectionUtils.emptyCollection());
        versions.add(newVersion);
        document.setVersions(versions);

        document.setUpdatedAt(new Date());
        document.setUpdatedBy(username);

        // Save all changes
        DocumentInformation updatedDocument = documentRepository.save(document);

        // Send sync event for reprocessing
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

        // Soft delete in database
        document.setDeleted(true);
        document.setUpdatedAt(new Date());
        document.setUpdatedBy(username);
        documentRepository.save(document);

        // Send delete event
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

    public byte[] getDocumentVersionContent(String documentId, Integer versionNumber, String username) throws IOException {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        DocumentInformation document = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Find the specific version
        DocumentVersion targetVersion = document.getVersion(versionNumber)
                .orElseThrow(() -> new InvalidDocumentException("Version not found"));

        // Read and return the file content
        try (InputStream in = Files.newInputStream(Path.of(targetVersion.getFilePath()))) {
            return in.readAllBytes();
        }
    }

    public DocumentInformation revertToVersion(String documentId, Integer versionNumber, String username) {
        // Get existing document and validate ownership
        DocumentInformation document = getDocumentDetails(documentId, username);
        if (!StringUtils.equals(document.getCreatedBy(), username)) {
            throw new InvalidDocumentException("Only document creator can revert versions");
        }

        // Find the specific version
        DocumentVersion versionToRevert = document.getVersion(versionNumber)
                .orElseThrow(() -> new InvalidDocumentException("Version not found"));

        int nextVersion = document.getCurrentVersion() + 1;

        // Create new version metadata - reuse existing file and content
        DocumentVersion newVersion = DocumentVersion.builder()
                .versionNumber(nextVersion)
                .filePath(versionToRevert.getFilePath())          // Reuse existing file path
                .thumbnailPath(versionToRevert.getThumbnailPath()) // Reuse existing thumbnail
                .filename(versionToRevert.getFilename())
                .fileSize(versionToRevert.getFileSize())
                .mimeType(versionToRevert.getMimeType())
                .status(DocumentStatus.COMPLETED)
                .language(versionToRevert.getLanguage())          // Reuse language detection
                .extractedMetadata(versionToRevert.getExtractedMetadata()) // Reuse extracted metadata
                .createdBy(username)
                .createdAt(new Date())
                .build();

        // Update document information - reuse existing data
        document.setStatus(DocumentStatus.PENDING); // Pending for indexing
        document.setFilename(versionToRevert.getFilename());
        document.setFilePath(versionToRevert.getFilePath());
        document.setThumbnailPath(versionToRevert.getThumbnailPath());
        document.setFileSize(versionToRevert.getFileSize());
        document.setMimeType(versionToRevert.getMimeType());
        document.setLanguage(versionToRevert.getLanguage());
        document.setExtractedMetadata(versionToRevert.getExtractedMetadata());
        document.setUpdatedAt(new Date());
        document.setUpdatedBy(username);
        document.setCurrentVersion(nextVersion);

        // Add new version to versions list
        List<DocumentVersion> versions = new ArrayList<>(document.getVersions());
        versions.add(newVersion);
        document.setVersions(versions);

        // Save changes
        DocumentInformation savedDocument = documentRepository.save(document);

        // Send sync event for Elasticsearch indexing
        publishEventService.sendSyncEvent(
                SyncEventRequest.builder()
                        .eventId(UUID.randomUUID().toString())
                        .userId(document.getUserId())
                        .documentId(documentId)
                        .versionNumber(versionToRevert.getVersionNumber())
                        .subject(EventType.REVERT_EVENT.name())
                        .triggerAt(LocalDateTime.now())
                        .build()
        );

        return savedDocument;
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

    private String getFileName(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(0, f.lastIndexOf(".")))
                .orElse("");
    }

    private byte[] getProcessingPlaceholder() throws IOException {
        try (InputStream is = processingPlaceholder.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private byte[] getErrorPlaceholder() throws IOException {
        try (InputStream is = errorPlaceholder.getInputStream()) {
            return is.readAllBytes();
        }
    }

}
