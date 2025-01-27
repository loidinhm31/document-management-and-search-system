package com.dms.processor.service;

import com.dms.processor.dto.DocumentContent;
import com.dms.processor.elasticsearch.DocumentIndex;
import com.dms.processor.elasticsearch.repository.DocumentIndexRepository;
import com.dms.processor.enums.DocumentStatus;
import com.dms.processor.enums.EventType;
import com.dms.processor.model.DocumentInformation;
import com.dms.processor.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessService {
    private final DocumentRepository documentRepository;
    private final DocumentIndexRepository documentIndexRepository;
    private final ContentExtractorService contentExtractorService;
    private final LanguageDetectionService languageDetectionService;
    private final ThumbnailService thumbnailService;

    public void indexDocument(DocumentInformation document, EventType eventType) {
        // Update status to PROCESSING
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);

        try {
            // First, remove existing document from index if it exists
            documentIndexRepository.deleteById(document.getId());

            DocumentIndex documentIndex = DocumentIndex.builder()
                    .id(document.getId())
                    .filename(document.getOriginalFilename())
                    .content(document.getContent())
                    .userId(document.getUserId())
                    .mimeType(document.getMimeType())
                    .documentType(document.getDocumentType())
                    .major(document.getMajor())
                    .courseCode(document.getCourseCode())
                    .courseLevel(document.getCourseLevel())
                    .category(document.getCategory())
                    .tags(document.getTags())
                    .fileSize(document.getFileSize())
                    .sharingType(document.getSharingType())
                    .sharedWith(document.getSharedWith())
                    .deleted(document.isDeleted())
                    .status(document.getStatus())
                    .createdAt(document.getCreatedAt())
                    .build();

            // Save to create initial index
            documentIndexRepository.save(documentIndex);

            // Continue process document for SYNC_EVENT or UPDATE_EVENT_WITH_FILE
            if (eventType.equals(EventType.SYNC_EVENT) || eventType.equals(EventType.UPDATE_EVENT_WITH_FILE)) {
                // Extract content and metadata
                DocumentContent extractedContent = contentExtractorService.extractContent(Path.of(document.getFilePath()));
                if (StringUtils.isNotEmpty(extractedContent.content())) {
                    // Detect language
                    Optional<String> detectedLanguage = languageDetectionService.detectLanguage(extractedContent.content());


                    // Update MongoDB document
                    document.setContent(extractedContent.content().trim());
                    document.setExtractedMetadata(extractedContent.metadata());
                    detectedLanguage.ifPresent(document::setLanguage);
                    document.setStatus(DocumentStatus.COMPLETED);
                    document.setProcessingError(null);
                    document.setUpdatedAt(new Date());
                    documentRepository.save(document);

                    // Update Elasticsearch document
                    documentIndex.setStatus(DocumentStatus.COMPLETED);
                    documentIndex.setContent(extractedContent.content().trim());
                    documentIndex.setExtractedMetadata(extractedContent.metadata());
                    detectedLanguage.ifPresent(documentIndex::setLanguage);
                    documentIndexRepository.save(documentIndex);

                    log.info("Successfully processed and indexed document: {}", document.getId());
                } else {
                    log.warn("No content extracted for document: {}", document.getId());
                    document.setStatus(DocumentStatus.FAILED);
                    document.setProcessingError("No content could be extracted");
                    documentRepository.save(document);
                }
            } else if (eventType.equals(EventType.UPDATE_EVENT)) {
                // Update MongoDB document
                document.setStatus(DocumentStatus.COMPLETED);
                document.setUpdatedAt(new Date());
                documentRepository.save(document);

                // Update Elasticsearch document
                documentIndex.setStatus(DocumentStatus.COMPLETED);
                documentIndexRepository.save(documentIndex);
                log.info("Successfully updated and indexed document: {}", document.getId());
            }
        } catch (Exception e) {
            log.error("Error indexing document: {}", document.getId(), e);
            document.setStatus(DocumentStatus.FAILED);
            document.setProcessingError(e.getMessage());
            document.setUpdatedAt(new Date());
            documentRepository.save(document);
            throw new RuntimeException("Failed to index document", e);
        }
    }

    public void deleteDocumentFromIndex(String documentId) {
        try {
            documentIndexRepository.deleteById(documentId);
            log.info("Successfully deleted document {} from index", documentId);
        } catch (Exception e) {
            log.error("Error deleting document {} from index", documentId, e);
            throw new RuntimeException("Failed to delete document from index", e);
        }
    }

    public void generateAndSaveThumbnail(DocumentInformation documentInformation) throws IOException {
        // Generate thumbnail
        byte[] thumbnailData = thumbnailService.generateThumbnail(
                Path.of(documentInformation.getFilePath()),
                documentInformation.getDocumentType(),
                documentInformation.getContent()
        );

        // Create thumbnail directory if it doesn't exist
        Path thumbnailDir = Path.of(documentInformation.getFilePath()).getParent().resolve("thumbnails");
        Files.createDirectories(thumbnailDir);

        // Save thumbnail
        String thumbnailFilename = documentInformation.getId() + "_thumb.png";
        Path thumbnailPath = thumbnailDir.resolve(thumbnailFilename);
        Files.write(thumbnailPath, thumbnailData);
        documentInformation.setThumbnailPath(thumbnailPath.toString());
        documentRepository.save(documentInformation);
    }

    public void cleanThumbnail(DocumentInformation documentInformation) {
        // Clear thumbnail path since we're updating the file
        String oldThumbnailPath = documentInformation.getThumbnailPath();
        documentInformation.setThumbnailPath(null);

        // Delete old files
        try {
            // Delete old main file
            Path oldFilePath = Path.of(documentInformation.getFilePath());
            Files.deleteIfExists(oldFilePath);

            // Delete old thumbnail if exists
            if (StringUtils.isNotEmpty(oldThumbnailPath)) {
                Path oldThumbnailPathFile = Path.of(oldThumbnailPath);
                Files.deleteIfExists(oldThumbnailPathFile);
            }
        } catch (IOException e) {
            log.error("Error deleting old files for document: {}", documentInformation.getId(), e);
        }
    }
}