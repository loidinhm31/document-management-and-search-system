package com.dms.search.service;

import com.dms.search.elasticsearch.DocumentIndex;
import com.dms.search.elasticsearch.repository.DocumentIndexRepository;
import com.dms.search.model.DocumentInformation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {
    private final DocumentIndexRepository documentIndexRepository;

    public void indexDocument(DocumentInformation document) {
        try {
            DocumentIndex documentIndex = DocumentIndex.builder()
                    .id(document.getId())
                    .filename(document.getOriginalFilename())
                    .content(document.getContent())
                    .userId(document.getUserId())
                    .mimeType(document.getMimeType())
                    .documentType(document.getDocumentType())
                    .major(document.getMajor().getCode())
                    .courseCode(document.getCourseCode())
                    .courseLevel(document.getCourseLevel())
                    .category(document.getCategory())
                    .tags(document.getTags())
                    .fileSize(document.getFileSize())
                    .createdAt(document.getCreatedAt())
                    .build();

            documentIndexRepository.save(documentIndex);
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
