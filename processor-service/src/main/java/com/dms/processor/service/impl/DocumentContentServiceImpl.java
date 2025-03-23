package com.dms.processor.service.impl;

import com.dms.processor.model.DocumentContent;
import com.dms.processor.repository.DocumentContentRepository;
import com.dms.processor.service.DocumentContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentContentServiceImpl implements DocumentContentService {
    private final DocumentContentRepository documentContentRepository;

    @Override
    public void saveVersionContent(String documentId, Integer versionNumber,
                                   String content, Map<String, String> metadata) {
        String contentId = generateContentId(documentId, versionNumber);

        DocumentContent docContent = DocumentContent.builder()
                .id(contentId)
                .documentId(documentId)
                .versionNumber(versionNumber)
                .content(content.trim())
                .extractedMetadata(metadata)
                .build();

        documentContentRepository.save(docContent);
    }

    @Override
    public Optional<DocumentContent> getVersionContent(String documentId, Integer versionNumber) {
        return documentContentRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber);
    }

    private String generateContentId(String documentId, Integer versionNumber) {
        return String.format("%s-v%d", documentId, versionNumber);
    }
}