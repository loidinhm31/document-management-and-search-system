package com.dms.processor.service;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.elasticsearch.DocumentIndex;
import com.dms.processor.elasticsearch.repository.DocumentIndexRepository;
import com.dms.processor.enums.DocumentStatus;
import com.dms.processor.enums.EventType;
import com.dms.processor.exception.DocumentProcessingException;
import com.dms.processor.mapper.DocumentIndexMapper;
import com.dms.processor.model.DocumentInformation;
import com.dms.processor.model.DocumentVersion;
import com.dms.processor.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessService {
    private final DocumentRepository documentRepository;
    private final DocumentIndexRepository documentIndexRepository;
    private final ContentExtractorService contentExtractorService;
    private final LanguageDetectionService languageDetectionService;
    private final ThumbnailService thumbnailService;
    private final DocumentIndexMapper documentIndexMapper;
    private final DocumentContentService documentContentService;


    @Transactional
    public void processDocument(DocumentInformation document, EventType eventType) {
        try {
            // Update status to PROCESSING
            document.setStatus(DocumentStatus.PROCESSING);
            document.setProcessingError(null);
            documentRepository.save(document);

            switch (eventType) {
                case SYNC_EVENT, UPDATE_EVENT_WITH_FILE -> processFullDocument(document);
                case UPDATE_EVENT -> processMetadataUpdate(document);
                default -> throw new IllegalArgumentException("Unsupported event type: " + eventType);
            }
        } catch (Exception e) {
            handleProcessingError(document, e);
            throw new DocumentProcessingException("Failed to process document", e);
        }
    }

    private void processFullDocument(DocumentInformation document) {
        Integer versionNumber = document.getCurrentVersion();
        if (Objects.isNull(versionNumber)) {
            throw new DocumentProcessingException("No version number provided");
        }

        DocumentVersion documentVersion = document.getVersion(versionNumber)
                .orElseThrow(() -> new DocumentProcessingException("Version not found"));

        DocumentExtractContent extractedContent = extractAndProcessContent(documentVersion);
        updateDocumentWithContent(document, documentVersion, extractedContent);
        // Generate thumbnail if needed (base on extracted content)
        handleThumbnail(document);
        indexDocument(document);
    }

    private void processMetadataUpdate(DocumentInformation document) {
        document.setStatus(DocumentStatus.COMPLETED);
        documentRepository.save(document);

        indexDocument(document);
    }

    private DocumentExtractContent extractAndProcessContent(DocumentVersion documentVersion) {
        Path filePath = Path.of(documentVersion.getFilePath());
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
        document.setUpdatedAt(new Date());
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
        document.setUpdatedAt(new Date());

        documentRepository.save(document);
    }

    public void deleteDocumentFromIndex(String documentId) {
        try {
            documentIndexRepository.deleteById(documentId);
            log.info("Successfully deleted document {} from index", documentId);
        } catch (Exception e) {
            log.error("Error deleting document {} from index", documentId, e);
            throw new DocumentProcessingException("Failed to delete document from index", e);
        }
    }

    public void handleThumbnail(DocumentInformation document) {
        try {
            generateAndSaveThumbnail(document);
        } catch (IOException e) {
            log.error("Error handling thumbnail for document: {}", document.getId(), e);
            throw new DocumentProcessingException("Failed to handle thumbnail", e);
        }
    }

    private void generateAndSaveThumbnail(DocumentInformation document) throws IOException {
        byte[] thumbnailData = thumbnailService.generateThumbnail(
                Path.of(document.getFilePath()),
                document.getDocumentType(),
                document.getContent()
        );

        Path thumbnailPath = saveThumbnailToFile(document, thumbnailData);
        document.setThumbnailPath(thumbnailPath.toString());
        documentRepository.save(document);
    }

    private Path saveThumbnailToFile(DocumentInformation document, byte[] thumbnailData) throws IOException {
        Path thumbnailDir = Path.of(document.getFilePath()).getParent().resolve("thumbnails");
        Files.createDirectories(thumbnailDir);

        String thumbnailFilename = String.format("%s_v%d_thumb.png", document.getId(), document.getCurrentVersion());
        Path thumbnailPath = thumbnailDir.resolve(thumbnailFilename);
        Files.write(thumbnailPath, thumbnailData);
        return thumbnailPath;
    }

}