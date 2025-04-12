package com.dms.document.search.service;

import com.dms.document.search.dto.DocumentResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DocumentRecommendationService {
    Page<DocumentResponseDto> getRecommendations(String documentId, Boolean favoriteOnly, String username, Pageable pageable);
}
