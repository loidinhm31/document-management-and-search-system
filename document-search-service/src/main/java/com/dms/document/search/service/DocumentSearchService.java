package com.dms.document.search.service;

import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.DocumentSearchRequest;
import com.dms.document.search.dto.SuggestionRequest;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Interface for document search operations
 */
public interface DocumentSearchService {

    /**
     * Search for documents based on specified criteria
     *
     * @param request  the search request containing search criteria and filters
     * @param username the username of the user performing the search
     * @return a page of document response DTOs matching the search criteria
     */
    Page<DocumentResponseDto> searchDocuments(DocumentSearchRequest request, String username);

    /**
     * Get search suggestions based on a partial query
     *
     * @param request  the suggestion request containing the partial query and filters
     * @param username the username of the user requesting suggestions
     * @return a list of suggestion strings
     */
    List<String> getSuggestions(SuggestionRequest request, String username);
}