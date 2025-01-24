package com.dms.search.service;

import com.dms.search.dto.DocumentContent;
import com.dms.search.elasticsearch.DocumentIndex;
import com.dms.search.elasticsearch.repository.DocumentIndexRepository;
import com.dms.search.enums.DocumentStatus;
import com.dms.search.enums.EventType;
import com.dms.search.model.DocumentInformation;
import com.dms.search.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessService {
    private final DocumentRepository documentRepository;
    private final DocumentIndexRepository documentIndexRepository;
    private final ContentExtractorService contentExtractorService;

    public void indexDocument(DocumentInformation document, EventType eventType) {
        // Update status to PROCESSING
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);

        try {
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
                    .isShared(document.isShared())
                    .createdAt(document.getCreatedAt())
                    .build();

            documentIndexRepository.save(documentIndex);

            // Continue process document
            if (eventType.equals(EventType.SYNC_EVENT) || eventType.equals(EventType.UPDATE_EVENT_WITH_FILE)) {
                // Extract content and metadata
                DocumentContent extractedContent = contentExtractorService.extractContent(Path.of(document.getFilePath()));
                if (StringUtils.isNotEmpty(extractedContent.content())) {
                    document.setContent(extractedContent.content());
                    document.setExtractedMetadata(extractedContent.metadata());
                    document.setStatus(DocumentStatus.COMPLETED);
                    document.setUpdatedAt(new Date());
                    documentRepository.save(document);

                    documentIndex.setStatus(DocumentStatus.COMPLETED);
                    documentIndex.setContent(extractedContent.content());
                    document.setExtractedMetadata(extractedContent.metadata());
                    document.setUpdatedAt(document.getUpdatedAt());
                    documentIndexRepository.save(documentIndex);
                }
            }
            log.info("Successfully indexed document: {}", document.getId());
        } catch (Exception e) {
            log.error("Error indexing document: {}", document.getId(), e);
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
