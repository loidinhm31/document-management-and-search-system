package com.dms.processor.service;

import com.dms.processor.dto.DocumentContent;
import com.dms.processor.elasticsearch.DocumentIndex;
import com.dms.processor.elasticsearch.repository.DocumentIndexRepository;
import com.dms.processor.enums.DocumentStatus;
import com.dms.processor.enums.EventType;
import com.dms.processor.exception.DocumentProcessingException;
import com.dms.processor.mapper.DocumentIndexMapper;
import com.dms.processor.model.DocumentInformation;
import com.dms.processor.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

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

    @Transactional
    public void processDocument(DocumentInformation document, EventType eventType) {
        try {
            // Update status to PROCESSING
            updateDocumentStatus(document, DocumentStatus.PROCESSING);

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
        DocumentContent extractedContent = extractAndProcessContent(document);
        updateDocumentWithContent(document, extractedContent);
        indexDocument(document);
    }

    private void processMetadataUpdate(DocumentInformation document) {
        updateDocumentStatus(document, DocumentStatus.COMPLETED);
        indexDocument(document);
    }

    private DocumentContent extractAndProcessContent(DocumentInformation document) {
        DocumentContent extractedContent = contentExtractorService.extractContent(Path.of(document.getFilePath()));
        if (extractedContent.content().isEmpty()) {
            throw new DocumentProcessingException("No content could be extracted");
        }
        return extractedContent;
    }

    private void updateDocumentWithContent(DocumentInformation document, DocumentContent content) {
        document.setContent(content.content().trim());
        document.setExtractedMetadata(content.metadata());
        languageDetectionService.detectLanguage(content.content())
                .ifPresent(document::setLanguage);
        document.setStatus(DocumentStatus.COMPLETED);
        document.setProcessingError(null);
        document.setUpdatedAt(new Date());
        documentRepository.save(document);
    }

    private void indexDocument(DocumentInformation document) {
        documentIndexRepository.deleteById(document.getId());
        DocumentIndex documentIndex = documentIndexMapper.toDocumentIndex(document);
        documentIndexRepository.save(documentIndex);
        log.info("Successfully indexed document: {}", document.getId());
    }

    private void updateDocumentStatus(DocumentInformation document, DocumentStatus status) {
        document.setStatus(status);
        documentRepository.save(document);
    }

    private void handleProcessingError(DocumentInformation document, Exception e) {
        log.error("Error processing document: {}", document.getId(), e);
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

        String thumbnailFilename = document.getId() + "_thumb.png";
        Path thumbnailPath = thumbnailDir.resolve(thumbnailFilename);
        Files.write(thumbnailPath, thumbnailData);
        return thumbnailPath;
    }

    public void cleanThumbnail(DocumentInformation document) {
        String oldThumbnailPath = document.getThumbnailPath();
        document.setThumbnailPath(null);

        if (oldThumbnailPath != null) {
            try {
                Files.deleteIfExists(Path.of(oldThumbnailPath));
            } catch (IOException e) {
                log.error("Error deleting thumbnail for document: {}", document.getId(), e);
                throw new DocumentProcessingException("Failed to clean thumbnail", e);
            }
        }
    }

}