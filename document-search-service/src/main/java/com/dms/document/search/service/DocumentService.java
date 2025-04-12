package com.dms.document.search.service;

import com.dms.document.search.dto.DocumentSearchCriteria;
import com.dms.document.search.model.DocumentInformation;
import org.springframework.data.domain.Page;

public interface DocumentService {
    Page<DocumentInformation> getUserDocuments(String username, DocumentSearchCriteria criteria, int page, int size);
}
