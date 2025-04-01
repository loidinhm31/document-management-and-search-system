package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.DocumentUpdateRequest;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.dto.ThumbnailResponse;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.*;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.exception.UnsupportedDocumentTypeException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.model.DocumentVersion;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import com.dms.document.interaction.service.*;
import com.dms.document.interaction.utils.DocumentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {
    @Value("${app.document.max-size-mb}")
    private DataSize maxFileSize;

    @Value("${app.document.placeholder.processing}")
    private Resource processingPlaceholder;

    @Value("${app.document.placeholder.error}")
    private Resource errorPlaceholder;

    private final DocumentRepository documentRepository;
    private final DocumentUserHistoryRepository documentUserHistoryRepository;
    private final DocumentNotificationService documentNotificationService;
    private final S3Service s3Service;
    private final PublishEventService publishEventService;
    private final DocumentPreferencesService documentPreferencesService;
    private final UserClient userClient;

    @Override
    public DocumentInformation uploadDocument(MultipartFile file,
                                              String summary,
                                              Set<String> courseCodes,
                                              Set<String> majors,
                                              String level,
                                              Set<String> categories,
                                              Set<String> tags,
                                              String username) throws IOException {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }

        UserResponse userResponse = response.getBody();

        if (!(Objects.equals(userResponse.role().roleName(), AppRole.ROLE_USER) ||
              Objects.equals(userResponse.role().roleName(), AppRole.ROLE_MENTOR))) {
            throw new InvalidDataAccessResourceUsageException("Invalid role");
        }

        validateDocument(file);
        DocumentType documentType = DocumentUtils.determineDocumentType(file.getContentType());

        // Upload new file to S3
        String s3Key = s3Service.uploadFile(file, "documents");

        // Create new version metadata
        int nextVersion = 0;
        DocumentVersion newVersion = DocumentVersion.builder()
                .versionNumber(nextVersion)
                .filePath(s3Key)
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .documentType(documentType)
                .status(DocumentStatus.PENDING)
                .createdBy(username)
                .createdAt(Instant.now())
                .build();

        // Initialize sets if they're null
        Set<String> finalMajors = majors != null ? majors : new HashSet<>();
        Set<String> finalCourseCodes = courseCodes != null ? courseCodes : new HashSet<>();
        Set<String> finalCategories = categories != null ? categories : new HashSet<>();
        Set<String> finalTags = tags != null ? tags : new HashSet<>();

        // Create document with PENDING status
        DocumentInformation document = DocumentInformation.builder()
                .status(DocumentStatus.PENDING)
                .filename(file.getOriginalFilename())
                .filePath(s3Key)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .documentType(documentType)
                .summary(summary)
                .majors(finalMajors)
                .courseCodes(finalCourseCodes)
                .courseLevel(level)
                .categories(finalCategories)
                .tags(finalTags)
                .userId(userResponse.userId().toString())
                .sharingType(SharingType.PRIVATE)
                .sharedWith(new HashSet<>())
                .deleted(false)
                .currentVersion(nextVersion)
                .versions(List.of(newVersion))
                .createdAt(Instant.now())
                .createdBy(username)
                .updatedAt(Instant.now())
                .updatedBy(username)
                .recommendationCount(0)
                .build();

        // Save data
        DocumentInformation savedDocument = documentRepository.save(document);
        log.info("Saved document: {}", savedDocument.getFilename());

        // Send sync event for processing
        CompletableFuture.runAsync(() -> {
            // History
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(savedDocument.getId())
                    .userDocumentActionType(UserDocumentActionType.UPLOAD_DOCUMENT)
                    .version(savedDocument.getCurrentVersion())
                    .detail(String.format("%s - %s KB",
                            savedDocument.getFilename(),
                            savedDocument.getFileSize())
                    )
                    .createdAt(Instant.now())
                    .build());

            publishEventService.sendSyncEvent(
                    SyncEventRequest.builder()
                            .eventId(UUID.randomUUID().toString())
                            .userId(userResponse.userId().toString())
                            .documentId(savedDocument.getId())
                            .subject(EventType.SYNC_EVENT.name())
                            .triggerAt(Instant.now())
                            .build()
            );
        });

        return savedDocument;
    }

    @Override
    public ThumbnailResponse getDocumentThumbnail(String documentId, String username) throws IOException {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        DocumentInformation document = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userResponse.userId().toString())
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
            byte[] thumbnailData = s3Service.downloadFile(document.getThumbnailPath());
            return ThumbnailResponse.builder()
                    .data(thumbnailData)
                    .status(HttpStatus.OK)
                    .isPlaceholder(false)
                    .build();
        } catch (Exception e) {
            log.error("Error getting thumbnail from S3: {}", e.getMessage());
            return ThumbnailResponse.builder()
                    .data(getErrorPlaceholder())
                    .status(HttpStatus.OK)
                    .isPlaceholder(true)
                    .build();
        }
    }

    @Override
    public byte[] getDocumentContent(String documentId, String username, String action, Boolean history) throws IOException {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        DocumentInformation documentInformation;
        if (userResponse.role().roleName().equals(AppRole.ROLE_ADMIN)) {
            documentInformation = documentRepository.findAccessibleDocumentById(documentId)
                    .orElseThrow(() -> new InvalidDocumentException("Document not found"));
        } else {
            documentInformation = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userResponse.userId().toString())
                    .orElseThrow(() -> new InvalidDocumentException("Document not found"));
        }

        byte[] fileContent = s3Service.downloadFile(documentInformation.getFilePath());
        if (Objects.nonNull(fileContent) && StringUtils.equals(action, "download") && BooleanUtils.isTrue(history)) {
            CompletableFuture.runAsync(() -> {
                // History
                documentUserHistoryRepository.save(DocumentUserHistory.builder()
                        .userId(userResponse.userId().toString())
                        .documentId(documentId)
                        .userDocumentActionType(UserDocumentActionType.DOWNLOAD_FILE)
                        .version(documentInformation.getCurrentVersion())
                        .detail(String.format("%s - %s - %s KB",
                                documentInformation.getFilename(),
                                documentInformation.getLanguage(),
                                documentInformation.getFileSize())
                        )
                        .createdAt(Instant.now())
                        .build());

                documentPreferencesService.recordInteraction(userResponse.userId(), documentId, InteractionType.DOWNLOAD);
            });
        }
        return fileContent;
    }

    @Override
    public DocumentInformation getDocumentDetails(String documentId, String username, Boolean history) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        DocumentInformation documentInformation;
        if (userResponse.role().roleName().equals(AppRole.ROLE_ADMIN)) {
            documentInformation = documentRepository.findAccessibleDocumentById(documentId)
                    .orElseThrow(() -> new InvalidDocumentException("Document not found"));
        } else {
            documentInformation = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userResponse.userId().toString())
                    .orElseThrow(() -> new InvalidDocumentException("Document not found"));
        }
        documentInformation.setContent(null);

        if (BooleanUtils.isTrue(history)) {
            CompletableFuture.runAsync(() -> {
                // History
                documentUserHistoryRepository.save(DocumentUserHistory.builder()
                        .userId(userResponse.userId().toString())
                        .documentId(documentId)
                        .userDocumentActionType(UserDocumentActionType.VIEW_DOCUMENT)
                        .version(documentInformation.getCurrentVersion())
                        .detail(String.format("%s - %s - %s KB",
                                documentInformation.getFilename(),
                                documentInformation.getLanguage(),
                                documentInformation.getFileSize()))
                        .createdAt(Instant.now())
                        .build());

                // Interaction for pref
                documentPreferencesService.recordInteraction(userResponse.userId(), documentId, InteractionType.VIEW);
            });
        }

        return documentInformation;
    }

    @Override
    public DocumentInformation updateDocument(
            String documentId,
            DocumentUpdateRequest documentUpdateRequest,
            String username) {

        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userResponse.userId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Update fields if provided
        if (Objects.nonNull(document)) {
            document.setSummary(documentUpdateRequest.summary());
            document.setCourseCodes(documentUpdateRequest.courseCodes());
            document.setMajors(documentUpdateRequest.majors());
            document.setCourseLevel(documentUpdateRequest.level());
            document.setCategories(documentUpdateRequest.categories());
            document.setTags(documentUpdateRequest.tags());
        }
        document.setUpdatedAt(Instant.now());
        document.setUpdatedBy(username);

        DocumentInformation updatedDocument = documentRepository.save(document);

        CompletableFuture.runAsync(() -> {
            // History
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(document.getUserId())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.UPDATE_DOCUMENT)
                    .version(document.getCurrentVersion())
                    .createdAt(Instant.now())
                    .build());

            // Send sync event for indexing update
            publishEventService.sendSyncEvent(SyncEventRequest.builder()
                    .eventId(UUID.randomUUID().toString())
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .subject(EventType.UPDATE_EVENT.name())
                    .triggerAt(Instant.now())
                    .build());
        });

        return updatedDocument.withContent(null);
    }

    @Override
    public DocumentInformation updateDocumentWithFile(
            String documentId,
            MultipartFile file,
            DocumentUpdateRequest documentUpdateRequest,
            String username) throws IOException {

        // Get existing document
        DocumentInformation document = getDocumentDetails(documentId, username, false);

        validateDocument(file);

        DocumentType documentType = DocumentUtils.determineDocumentType(file.getContentType());

        // Upload new file to S3
        String s3Key = s3Service.uploadFile(file, "documents");

        // Create new version metadata
        int nextVersion = (document.getCurrentVersion() != null ? document.getCurrentVersion() : 0) + 1;
        DocumentVersion newVersion = DocumentVersion.builder()
                .versionNumber(nextVersion)
                .filePath(s3Key)
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .documentType(documentType)
                .status(DocumentStatus.PENDING)
                .createdBy(username)
                .createdAt(Instant.now())
                .build();

        // Update document information
        document.setStatus(DocumentStatus.PENDING); // Reset to pending for reprocessing
        document.setFilename(file.getOriginalFilename());
        document.setFilePath(s3Key);
        document.setThumbnailPath(null); // Reset thumbnail - will be generated for new version
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDocumentType(documentType);

        // Update metadata
        document.setSummary(documentUpdateRequest.summary());
        document.setCourseCodes(documentUpdateRequest.courseCodes());
        document.setMajors(documentUpdateRequest.majors());
        document.setCourseLevel(documentUpdateRequest.level());
        document.setCategories(documentUpdateRequest.categories());
        document.setTags(documentUpdateRequest.tags());

        document.setUpdatedAt(Instant.now());
        document.setUpdatedBy(username);

        document.setCurrentVersion(nextVersion);
        // Add new version to versions list
        List<DocumentVersion> versions = new ArrayList<>(
                CollectionUtils.isNotEmpty(document.getVersions()) ? document.getVersions() : CollectionUtils.emptyCollection());
        versions.add(newVersion);
        document.setVersions(versions);

        document.setUpdatedAt(Instant.now());
        document.setUpdatedBy(username);

        // Save all changes
        DocumentInformation updatedDocument = documentRepository.save(document);

        // Send sync event for reprocessing
        CompletableFuture.runAsync(() -> {
            // History
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(document.getUserId())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.UPDATE_DOCUMENT_FILE)
                    .version(document.getCurrentVersion())
                    .detail(file.getOriginalFilename())
                    .createdAt(Instant.now())
                    .build());

            publishEventService.sendSyncEvent(
                    SyncEventRequest.builder()
                            .eventId(UUID.randomUUID().toString())
                            .userId(document.getUserId())
                            .documentId(documentId)
                            .subject(EventType.UPDATE_EVENT_WITH_FILE.name())
                            .triggerAt(Instant.now())
                            .build());

            // Notify about new file version
            documentNotificationService.handleFileVersionNotification(
                    updatedDocument,
                    username,
                    updatedDocument.getCurrentVersion()
            );
        });

        return updatedDocument;
    }

    @Override
    public void deleteDocument(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userResponse.userId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Soft delete in database
        document.setDeleted(true);
        document.setUpdatedAt(Instant.now());
        document.setUpdatedBy(username);
        documentRepository.save(document);

        // Send delete event
        CompletableFuture.runAsync(() -> {
            // History
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(document.getUserId())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.DELETE_DOCUMENT)
                    .version(document.getCurrentVersion())
                    .detail(String.format("%s - v%s", document.getFilename(), document.getCurrentVersion()))
                    .createdAt(Instant.now())
                    .build());

            publishEventService.sendSyncEvent(
                    SyncEventRequest.builder()
                            .eventId(UUID.randomUUID().toString())
                            .userId(userResponse.userId().toString())
                            .documentId(documentId)
                            .subject(EventType.DELETE_EVENT.name())
                            .triggerAt(Instant.now())
                            .build()
            );
        });
    }

    @Override
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

    @Override
    public byte[] getDocumentVersionContent(String documentId, Integer versionNumber, String username, String action, Boolean history) throws IOException {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        DocumentInformation documentInformation = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userResponse.userId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Find the specific version
        DocumentVersion targetVersion = documentInformation.getVersion(versionNumber)
                .orElseThrow(() -> new InvalidDocumentException("Version not found"));

        byte[] fileContent = s3Service.downloadFile(targetVersion.getFilePath());
        if (Objects.nonNull(fileContent) && StringUtils.equals(action, "download") && BooleanUtils.isTrue(history)) {
            // History
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.DOWNLOAD_VERSION)
                    .version(documentInformation.getCurrentVersion())
                    .detail(String.format("%s - %s - %s KB - v%s",
                            targetVersion.getFilename(),
                            targetVersion.getLanguage(),
                            targetVersion.getFileSize(),
                            targetVersion.getVersionNumber())
                    )
                    .createdAt(Instant.now())
                    .build());

            CompletableFuture.runAsync(() -> documentPreferencesService.recordInteraction(userResponse.userId(), documentId, InteractionType.DOWNLOAD));
        }
        return fileContent;
    }

    @Override
    public DocumentInformation revertToVersion(String documentId, Integer versionNumber, String username) {
        // Get existing document and validate ownership
        DocumentInformation document = getDocumentDetails(documentId, username, false);
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
                .documentType(versionToRevert.getDocumentType())
                .status(DocumentStatus.COMPLETED)
                .language(versionToRevert.getLanguage())          // Reuse language detection
                .extractedMetadata(versionToRevert.getExtractedMetadata()) // Reuse extracted metadata
                .createdBy(username)
                .createdAt(Instant.now())
                .build();

        // Update document information - reuse existing data
        document.setStatus(DocumentStatus.PENDING); // Pending for indexing
        document.setFilename(versionToRevert.getFilename());
        document.setFilePath(versionToRevert.getFilePath());
        document.setThumbnailPath(versionToRevert.getThumbnailPath());
        document.setFileSize(versionToRevert.getFileSize());
        document.setMimeType(versionToRevert.getMimeType());
        document.setDocumentType(versionToRevert.getDocumentType());
        document.setLanguage(versionToRevert.getLanguage());
        document.setExtractedMetadata(versionToRevert.getExtractedMetadata());
        document.setUpdatedAt(Instant.now());
        document.setUpdatedBy(username);
        document.setCurrentVersion(nextVersion);

        // Add new version to versions list
        List<DocumentVersion> versions = new ArrayList<>(document.getVersions());
        versions.add(newVersion);
        document.setVersions(versions);

        // Save changes
        DocumentInformation savedDocument = documentRepository.save(document);

        CompletableFuture.runAsync(() -> {
            // History
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(document.getUserId())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.REVERT_VERSION)
                    .version(document.getCurrentVersion())
                    .detail("Version " + versionToRevert.getVersionNumber())
                    .createdAt(Instant.now())
                    .build());

            // Send sync event for indexing document
            publishEventService.sendSyncEvent(
                    SyncEventRequest.builder()
                            .eventId(UUID.randomUUID().toString())
                            .userId(document.getUserId())
                            .documentId(documentId)
                            .versionNumber(versionToRevert.getVersionNumber())
                            .subject(EventType.REVERT_EVENT.name())
                            .triggerAt(Instant.now())
                            .build()
            );

            // Notify about document reversion
            documentNotificationService.handleRevertNotification(
                    savedDocument,
                    username,
                    versionNumber
            );
        });

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
        if (!DocumentType.isSupportedMimeType(mimeType)) {
            throw new UnsupportedDocumentTypeException("Unsupported document type: " + mimeType);
        }

        // Additional validation: check file extension matches MIME type
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = getFileExtension(originalFilename).toLowerCase();
            DocumentType documentType;
            try {
                documentType = DocumentType.fromMimeType(mimeType);
            } catch (UnsupportedDocumentTypeException e) {
                throw new InvalidDocumentException("Invalid MIME type: " + mimeType);
            }

            boolean isValidExtension = switch (documentType) {
                case PDF -> extension.equals(".pdf");
                case WORD -> extension.equals(".doc");
                case WORD_DOCX -> extension.equals(".docx");
                case EXCEL -> extension.equals(".xls");
                case EXCEL_XLSX -> extension.equals(".xlsx");
                case POWERPOINT -> extension.equals(".ppt");
                case POWERPOINT_PPTX -> extension.equals(".pptx");
                case TEXT_PLAIN -> extension.equals(".txt");
                case CSV -> extension.equals(".csv");
                case XML -> extension.equals(".xml");
                case JSON -> extension.equals(".json");
                case MARKDOWN -> extension.equals(".md");
            };

            if (!isValidExtension) {
                throw new InvalidDocumentException(
                        String.format("File extension '%s' does not match the expected type '%s'",
                                extension, documentType.getDisplayName())
                );
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
