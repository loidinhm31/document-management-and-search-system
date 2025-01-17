package com.sdms.document.service;

import com.sdms.document.elasticsearch.DocumentIndex;
import com.sdms.document.elasticsearch.repository.DocumentIndexRepository;
import com.sdms.document.entity.Document;
import com.sdms.document.entity.DocumentMetadata;
import com.sdms.document.entity.User;
import com.sdms.document.enums.DocumentType;
import com.sdms.document.model.DocumentContent;
import com.sdms.document.repository.DocumentRepository;
import com.sdms.document.repository.UserRepository;
import com.sdms.document.utils.DocumentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.sdms.document.utils.DocumentUtils.determineDocumentType;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final ContentExtractor contentExtractor;
    private final DocumentIndexRepository documentIndexRepository;

    @Value("${app.document.storage.path}")
    private String storageBasePath;


    @Transactional(rollbackFor = Exception.class)
    public Document uploadDocument(MultipartFile file, String username) throws IOException {
        // Validate user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidDataAccessResourceUsageException("User not found"));

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

        // Create document entity
        Document document = new Document();
        document.setFilename(uniqueFilename);
        document.setOriginalFilename(originalFilename);
        document.setFilePath(relativePath);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDocumentType(DocumentUtils.determineDocumentType(file.getContentType()));
        document.setUser(user);
        document.setCreatedBy(username);
        document.setUpdatedBy(username);

        // Extract content and metadata
        DocumentContent extractedContent = contentExtractor.extractContent(fullPath);

        // Set extracted content for indexing
        document.setIndexedContent(extractedContent.getContent());

        // Add extracted metadata
        addExtractedMetadata(document, extractedContent.getMetadata());

        // Save document to database
        Document savedDocument = documentRepository.save(document);

        // Index document in Elasticsearch
        indexDocument(savedDocument);

        return savedDocument;
    }


    public byte[] getDocumentContent(UUID documentId, String username) throws IOException {
        Document document = documentRepository.findByIdAndUser_Username(documentId, username)
                .orElseThrow(() -> new InvalidDataAccessResourceUsageException("Document not found"));

        Path filePath = Path.of(storageBasePath, document.getFilePath());
        return Files.readAllBytes(filePath);
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

    private void addExtractedMetadata(Document document, Map<String, String> metadata) {
        metadata.forEach((key, value) -> {
            DocumentMetadata metadataEntity = new DocumentMetadata();
            metadataEntity.setDocument(document);
            metadataEntity.setKey(key);
            metadataEntity.setValue(value);
            document.getMetadata().add(metadataEntity);
        });
    }

    private void indexDocument(Document document) {
        DocumentIndex documentIndex = DocumentIndex.builder()
                .id(document.getId().toString())
                .filename(document.getOriginalFilename())
                .content(document.getIndexedContent())
                .userId(document.getUser().getUserId().toString())
                .mimeType(document.getMimeType())
                .documentType(determineDocumentType(document.getMimeType()))
                .fileSize(document.getFileSize())
                .createdAt(document.getCreatedAt())
                .metadata(convertMetadataToMap(document.getMetadata()))
                .build();

        documentIndexRepository.save(documentIndex);
    }


    private Map<String, String> convertMetadataToMap(Set<DocumentMetadata> metadata) {
        return metadata.stream()
                .collect(Collectors.toMap(
                        DocumentMetadata::getKey,
                        DocumentMetadata::getValue,
                        (existing, replacement) -> existing // Keep existing in case of duplicates
                ));
    }
}
