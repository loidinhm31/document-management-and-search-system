package com.dms.document.service;

import com.dms.document.client.UserClient;
import com.dms.document.dto.ApiResponse;
import com.dms.document.dto.DocumentContent;
import com.dms.document.dto.UserDto;
import com.dms.document.enums.*;
import com.dms.document.exception.InvalidDocumentException;
import com.dms.document.exception.UnsupportedDocumentTypeException;
import com.dms.document.model.DocumentInformation;
import com.dms.document.model.SyncEventRequest;
import com.dms.document.repository.DocumentRepository;
import com.dms.document.utils.DocumentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final PublishEventService publishEventService;
    @Value("${app.document.storage.path}")
    private String storageBasePath;

    @Value("${app.document.storage.max-file-size}")
    private DataSize maxFileSize;

    private final DocumentRepository documentRepository;
    private final UserClient userClient;
    private final ContentExtractorService contentExtractorService;

    public DocumentInformation uploadDocument(MultipartFile file,
                                              String courseCode,
                                              Major major,
                                              CourseLevel level,
                                              DocumentCategory category,
                                              Set<String> tags,
                                              String username) throws IOException {
        ApiResponse<UserDto> response = userClient.getUserByUsername(username);
        if (!response.isSuccess() || Objects.isNull(response.getData())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }

        UserDto userDto = response.getData();

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

        // Send sync event
        CompletableFuture.runAsync(() -> {
            publishEventService.sendSyncEvent(
                    SyncEventRequest.builder()
                            .eventId(UUID.randomUUID().toString())
                            .userId(userDto.getUserId().toString())
                            .documentId(savedDocument.getId())
                            .subject(EventType.SYNC_EVENT.name())
                            .triggerAt(LocalDateTime.now())
                            .build()
            );
        });

        return savedDocument;
    }


    public byte[] getDocumentContent(String documentId, String username) throws IOException {
        ApiResponse<UserDto> response = userClient.getUserByUsername(username);
        if (!response.isSuccess() || Objects.isNull(response.getData())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getData();

        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, userDto.getUserId().toString())
                .orElseThrow(() -> new InvalidDataAccessResourceUsageException("Document not found"));

        Path filePath = Path.of(storageBasePath, document.getFilePath());
        return Files.readAllBytes(filePath);
    }

    public DocumentInformation updateTags(String documentId, Set<String> tags, String username) {
        DocumentInformation document = documentRepository.findByIdAndUserId(documentId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        document.setTags(tags);
        document.setUpdatedAt(new Date());
        document.setUpdatedBy(username);

        DocumentInformation updatedDocument = documentRepository.save(document);

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
        // Create path structure: yyyy/MM/dd/filename
        LocalDate now = LocalDate.now();
        return String.format("%d/%02d/%02d/%s",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), filename);
    }

    private String getFileExtension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(f.lastIndexOf(".")))
                .orElse("");
    }

}
