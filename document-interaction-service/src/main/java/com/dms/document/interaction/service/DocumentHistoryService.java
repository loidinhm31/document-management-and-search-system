package com.dms.document.interaction.service;

import com.dms.document.interaction.repository.UserDocumentHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentHistoryService {

    private final UserDocumentHistoryRepository userDocumentHistoryRepository;

    @Transactional(readOnly = true)
    public int getDocumentDownloadCount(String documentId) {
        Integer count = userDocumentHistoryRepository.getDownloadCountForDocument(documentId);
        return count != null ? count : 0;
    }
}