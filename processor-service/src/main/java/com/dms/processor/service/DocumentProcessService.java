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
}