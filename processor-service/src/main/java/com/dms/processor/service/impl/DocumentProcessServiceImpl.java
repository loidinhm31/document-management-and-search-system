package com.dms.processor.service.impl;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.enums.DocumentReportStatus;
import com.dms.processor.enums.DocumentStatus;
import com.dms.processor.enums.EventType;
import com.dms.processor.exception.DocumentProcessingException;
import com.dms.processor.mapper.DocumentIndexMapper;
import com.dms.processor.model.DocumentContent;
import com.dms.processor.model.DocumentInformation;
import com.dms.processor.model.DocumentVersion;
import com.dms.processor.opensearch.DocumentIndex;
import com.dms.processor.opensearch.repository.DocumentIndexRepository;
import com.dms.processor.repository.DocumentRepository;
import com.dms.processor.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessServiceImpl implements DocumentProcessService {
    private final DocumentRepository documentRepository;
    private final DocumentIndexRepository documentIndexRepository;
    private final ContentExtractorService contentExtractorService;
    private final LanguageDetectionService languageDetectionService;
    private final ThumbnailService thumbnailService;
    private final DocumentIndexMapper documentIndexMapper;
    private final DocumentContentService documentContentService;
    private final DocumentEmailService documentEmailService;
    private final S3Service s3Service;


    @Transactional
    @Override
    public void processDocument(DocumentInformation document, Integer versionNumber, EventType eventType) {
        Path tempFile = null;
        try {
            // Update status to PROCESSING
            document.setStatus(DocumentStatus.PROCESSING);
            document.setProcessingError(null);
            documentRepository.save(document);

            // Download file to temp location if needed
            if (eventType == EventType.SYNC_EVENT ||
                eventType == EventType.UPDATE_EVENT_WITH_FILE) {
                tempFile = s3Service.downloadToTemp(document.getFilePath());
                processFullDocument(document, tempFile);
            } else if (eventType == EventType.UPDATE_EVENT) {
                processMetadataUpdate(document);
            } else if (eventType == EventType.REVERT_EVENT) {
                processRevertContent(document, versionNumber);
            } else {
                throw new IllegalArgumentException("Unsupported event type: " + eventType);
            }
        } catch (Exception e) {
            handleProcessingError(document, e);
            throw new DocumentProcessingException("Failed to process document", e);
        } finally {
            if (tempFile != null) {
                s3Service.cleanup(tempFile);
            }
        }
    }

    @Transactional
    @Override
    public void handleReportStatus(String documentId, String userId, int times) {
        try {
            DocumentInformation document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found"));

            // Index the document to update search
            indexDocument(document);

            if (document.getReportStatus() == DocumentReportStatus.REJECTED) {
                // Send notification emails to reporters
                documentEmailService.sendDocumentReportRejectionNotifications(document, userId, times);

            } else if (document.getReportStatus() == DocumentReportStatus.RESOLVED) {
                // Send notifications to both favoriters and reporters
                documentEmailService.sendResolveNotifications(document, userId, times);

            } else if (document.getReportStatus() == DocumentReportStatus.REMEDIATED) {
                // Send notifications only to favoriters
                documentEmailService.sendReportRemediationNotifications(document, userId);
            }
        } catch (Exception e) {
            log.error("Error handling report status for document: {}", documentId, e);
            throw new DocumentProcessingException("Failed to process report status", e);
        }
    }

    private void processRevertContent(DocumentInformation document, Integer revertToVersionNumber) {
        // Get the document content for the version we want to revert to
        DocumentContent documentContent = documentContentService.getVersionContent(
                document.getId(),
                revertToVersionNumber
        ).orElseThrow(() -> new DocumentProcessingException(
                "Content not found for document: " + document.getId() +
                " version: " + document.getCurrentVersion()
        ));

        // Get current saved version
        DocumentVersion documentVersion = document.getVersion(document.getCurrentVersion())
                .orElseThrow(() -> new DocumentProcessingException("Version not found"));

        // Save content
        documentContentService.saveVersionContent(
                document.getId(),
                documentVersion.getVersionNumber(),
                documentContent.getContent(),
                documentContent.getExtractedMetadata()
        );

        // Update document with content and metadata from the content collection
        document.setContent(documentContent.getContent());
        document.setLanguage(documentVersion.getLanguage());
        document.setExtractedMetadata(documentContent.getExtractedMetadata());
        document.setStatus(DocumentStatus.COMPLETED);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);

        // Index the document
        indexDocument(document);

        log.info("Successfully processed reverted document: {}", document.getId());
    }

    private void processFullDocument(DocumentInformation document, Path tempFile) throws IOException {
        Integer versionNumber = document.getCurrentVersion();
        if (Objects.isNull(versionNumber)) {
            throw new DocumentProcessingException("No version number provided");
        }

        DocumentVersion documentVersion = document.getVersion(versionNumber)
                .orElseThrow(() -> new DocumentProcessingException("Version not found"));

        DocumentExtractContent extractedContent = extractAndProcessContent(tempFile);
        updateDocumentWithContent(document, documentVersion, extractedContent);
        // Generate thumbnail if needed (base on extracted content)
        handleThumbnail(document, documentVersion, tempFile);
        indexDocument(document);
    }

    private void processMetadataUpdate(DocumentInformation document) {
        document.setStatus(DocumentStatus.COMPLETED);
        documentRepository.save(document);

        indexDocument(document);
    }

    private DocumentExtractContent extractAndProcessContent(Path filePath) {
        DocumentExtractContent extractedContent = contentExtractorService.extractContent(filePath);

        if (extractedContent.content().isEmpty()) {
            throw new DocumentProcessingException("No content could be extracted");
        }
        return extractedContent;
    }

    private void updateDocumentWithContent(DocumentInformation document,
                                           DocumentVersion documentVersion,
                                           DocumentExtractContent documentExtractContent) {
        // Update document's current content
        document.setContent(documentExtractContent.content().trim());
        document.setExtractedMetadata(documentExtractContent.metadata());

        // Detect language if not already set
        if (StringUtils.isEmpty(documentVersion.getLanguage())) {
            languageDetectionService.detectLanguage(documentExtractContent.content())
                    .ifPresent(lang -> {
                        documentVersion.setLanguage(lang);
                        document.setLanguage(lang);
                    });
        }

        // Update version status
        documentVersion.setStatus(DocumentStatus.COMPLETED);
        document.setStatus(DocumentStatus.COMPLETED);

        // Update timestamps
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);

        documentContentService.saveVersionContent(
                document.getId(),
                documentVersion.getVersionNumber(),
                documentExtractContent.content(),
                documentExtractContent.metadata()
        );
    }

    private void indexDocument(DocumentInformation document) {
        DocumentIndex documentIndex = documentIndexMapper.toDocumentIndex(document);
        documentIndexRepository.save(documentIndex);
        log.info("Successfully indexed document: {}", document.getId());
    }

    private void handleProcessingError(DocumentInformation document, Exception e) {
        log.error("Error processing document: {}", document.getId(), e);

        // Get current version
        DocumentVersion currentVersion = document.getVersion(document.getCurrentVersion())
                .orElse(null);

        if (currentVersion != null) {
            // Update version status
            currentVersion.setStatus(DocumentStatus.FAILED);
            currentVersion.setProcessingError(e.getMessage());
        }

        // Update document status
        document.setStatus(DocumentStatus.FAILED);
        document.setProcessingError(e.getMessage());
        document.setUpdatedAt(Instant.now());

        documentRepository.save(document);
    }

    @Override
    public void deleteDocumentFromIndex(String documentId) {
        try {
            documentIndexRepository.deleteById(documentId);
            log.info("Successfully deleted document {} from index", documentId);
        } catch (Exception e) {
            log.error("Error deleting document {} from index", documentId, e);
            throw new DocumentProcessingException("Failed to delete document from index", e);
        }
    }

    private void handleThumbnail(DocumentInformation document, DocumentVersion documentVersion, Path tempFile) {
        try {
            generateAndSaveThumbnail(document, documentVersion, tempFile);
        } catch (IOException e) {
            log.error("Error handling thumbnail for document: {}", document.getId(), e);
            throw new DocumentProcessingException("Failed to handle thumbnail", e);
        }
    }

    private void generateAndSaveThumbnail(DocumentInformation document,
                                          DocumentVersion documentVersion,
                                          Path tempFile) throws IOException {
        byte[] thumbnailData = thumbnailService.generateThumbnail(
                tempFile,
                document.getDocumentType(),
                document.getContent()
        );

        // Generate a temp thumbnail file
        Path tempThumb = Files.createTempFile("thumb_", ".png");
        Files.write(tempThumb, thumbnailData);

        try {
            // Upload thumbnail to S3
            String thumbnailKey = s3Service.uploadFile(tempThumb, "thumbnails", "image/png");

            // Update document with S3 thumbnail path
            document.setThumbnailPath(thumbnailKey);
            documentVersion.setThumbnailPath(thumbnailKey);

            documentRepository.save(document);
        } finally {
            // Cleanup temp thumbnail
            Files.deleteIfExists(tempThumb);
        }
    }
}