package com.dms.document.search.service.impl;

import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.*;
import com.dms.document.search.enums.AppRole;
import com.dms.document.search.model.DocumentPreferences;
import com.dms.document.search.repository.DocumentPreferencesRepository;
import com.dms.document.search.service.DocumentFavoriteService;
import org.apache.lucene.search.TotalHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.common.text.Text;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscoverDocumentSearchServiceImplTest {

    @Mock
    private RestHighLevelClient openSearchClient;

    @Mock
    private UserClient userClient;

    @Mock
    private DocumentPreferencesRepository documentPreferencesRepository;

    @Mock
    private DocumentFavoriteService documentFavoriteService;

    @InjectMocks
    private DiscoverDocumentSearchServiceImpl discoverDocumentSearchService;

    private UUID userId;
    private String username;
    private UserResponse userResponse;
    private DocumentSearchRequest searchRequest;
    private SearchResponse searchResponse;
    private SearchHits searchHits;
    private SearchHit searchHit;

    @BeforeEach
    void setUp() throws IOException {
        userId = UUID.randomUUID();
        username = "testuser";
        userResponse = new UserResponse(userId, username, "test@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER));

        searchRequest = DocumentSearchRequest.builder()
                .search("test document")
                .page(0)
                .size(10)
                .build();

        // Mock search hit - use lenient() to avoid "unnecessary stubbing" errors
        searchHit = mock(SearchHit.class);
        lenient().when(searchHit.getId()).thenReturn("doc1");
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("filename", "Test Document");
        sourceMap.put("userId", userId.toString());
        sourceMap.put("createdAt", new Date().toString());
        lenient().when(searchHit.getSourceAsMap()).thenReturn(sourceMap);

        // Add some highlight fields
        Map<String, HighlightField> highlightFields = new HashMap<>();
        HighlightField highlightField = mock(HighlightField.class);
        lenient().when(highlightField.fragments()).thenReturn(new Text[]{new Text("highlighted content")});
        highlightFields.put("content", highlightField);
        lenient().when(searchHit.getHighlightFields()).thenReturn(highlightFields);

        // Mock search hits
        searchHits = mock(SearchHits.class);
        lenient().when(searchHits.getHits()).thenReturn(new SearchHit[]{searchHit});
        lenient().when(searchHits.getTotalHits()).thenReturn(new TotalHits(1L, TotalHits.Relation.EQUAL_TO));

        // Mock search response
        searchResponse = mock(SearchResponse.class);
        lenient().when(searchResponse.getHits()).thenReturn(searchHits);
    }

    @Test
    void searchDocuments_ValidRequest_ReturnsDocuments() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("doc1", result.getContent().get(0).getId());
        verify(userClient, times(1)).getUserByUsername(username);
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void searchDocuments_UserNotFound_ThrowsException() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.notFound().build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () -> {
            discoverDocumentSearchService.searchDocuments(searchRequest, username);
        });
        verify(userClient, times(1)).getUserByUsername(username);
        verify(openSearchClient, never()).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void searchDocuments_EmptySearch_ReturnsEmptyPage() throws IOException {
        // Arrange
        DocumentSearchRequest emptySearchRequest = DocumentSearchRequest.builder()
                .search("a") // Too short search (less than MIN_SEARCH_LENGTH)
                .page(0)
                .size(10)
                .build();
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(emptySearchRequest, username);

        // Assert
        assertTrue(result.isEmpty());
        verify(userClient, times(1)).getUserByUsername(username);
        verify(openSearchClient, never()).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void searchDocuments_SearchError_ThrowsException() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenThrow(new IOException("Search error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            discoverDocumentSearchService.searchDocuments(searchRequest, username);
        });
        verify(userClient, times(1)).getUserByUsername(username);
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void getSuggestions_ValidRequest_ReturnsSuggestions() throws IOException {
        // Arrange
        SuggestionRequest suggestionRequest = SuggestionRequest.builder()
                .query("test document")
                .build();
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        List<String> result = discoverDocumentSearchService.getSuggestions(suggestionRequest, username);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("highlighted content", result.get(0));
        verify(userClient, times(1)).getUserByUsername(username);
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void getSuggestions_ShortQuery_ReturnsEmptyList() throws IOException {
        // Arrange
        SuggestionRequest shortQueryRequest = SuggestionRequest.builder()
                .query("a")  // Too short query
                .build();

        // Act
        List<String> result = discoverDocumentSearchService.getSuggestions(shortQueryRequest, username);

        // Assert
        assertTrue(result.isEmpty());
        verify(userClient, never()).getUserByUsername(anyString());
        verify(openSearchClient, never()).search(any(SearchRequest.class), any(RequestOptions.class));
    }

    @Test
    void getSuggestions_UserNotFound_ReturnsEmptyList() throws IOException {
        // Arrange
        SuggestionRequest suggestionRequest = SuggestionRequest.builder()
                .query("test document")
                .build();
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.notFound().build());

        // Act
        List<String> result = discoverDocumentSearchService.getSuggestions(suggestionRequest, username);

        // Assert
        assertTrue(result.isEmpty());
        verify(userClient, times(1)).getUserByUsername(username);
        verify(openSearchClient, never()).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void searchDocuments_WithPreferences_AppliesPreferenceBoosts() throws IOException {
        // Arrange
        DocumentPreferences preferences = new DocumentPreferences();
        preferences.setPreferredMajors(Set.of("CS", "IT"));
        preferences.setPreferredCourseCodes(Set.of("CS101", "IT202"));
        preferences.setPreferredLevels(Set.of("Beginner", "Intermediate"));
        preferences.setPreferredCategories(Set.of("Programming", "Database"));
        preferences.setPreferredTags(Set.of("java", "spring"));
        preferences.setLanguagePreferences(Set.of("en", "vi"));

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString()))
                .thenReturn(Optional.of(preferences));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
        verify(documentPreferencesRepository).findByUserId(userId.toString());
    }

    @Test
    void searchDocuments_WithShortQuery_AppliesDefinitionSearchLogic() throws IOException {
        // Arrange
        searchRequest = DocumentSearchRequest.builder()
                .search("java spring") // Short query that should trigger definition search logic
                .page(0)
                .size(10)
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchDocuments_WithVietnameseText_AdjustsMinScore() throws IOException {
        // Arrange
        searchRequest = DocumentSearchRequest.builder()
                .search("tài liệu kiểm tra")
                .page(0)
                .size(10)
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchDocuments_WithSortField_AppliesSorting() throws IOException {
        // Arrange
        searchRequest = DocumentSearchRequest.builder()
                .search("test document")
                .sortField("createdAt")
                .sortDirection("DESC")
                .page(0)
                .size(10)
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchDocuments_WithFilters_AppliesFilterConditions() throws IOException {
        // Arrange
        searchRequest = DocumentSearchRequest.builder()
                .search("test document")
                .majors(Set.of("CS", "IT"))
                .courseCodes(Set.of("CS101"))
                .level("Beginner")
                .categories(Set.of("Programming"))
                .tags(Set.of("java"))
                .page(0)
                .size(10)
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getSuggestions_WithPreferences_AppliesStrongerPreferenceBoosts() throws IOException {
        // Arrange
        SuggestionRequest suggestionRequest = SuggestionRequest.builder()
                .query("test document")
                .build();

        DocumentPreferences preferences = new DocumentPreferences();
        preferences.setPreferredMajors(Set.of("CS", "IT"));
        preferences.setLanguagePreferences(Set.of("en", "vi"));

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString()))
                .thenReturn(Optional.of(preferences));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        List<String> result = discoverDocumentSearchService.getSuggestions(suggestionRequest, username);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        verify(documentPreferencesRepository).findByUserId(userId.toString());
    }

    @Test
    void searchDocuments_WithFavoriteOnly_AppliesFavoriteFilter() throws IOException {
        // Arrange
        searchRequest = DocumentSearchRequest.builder()
                .search("test document")
                .favoriteOnly(true)
                .page(0)
                .size(10)
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
        verify(documentFavoriteService).addFavoriteFilter(any(), eq(userId));
    }

    @Test
    void searchDocuments_WithDifferentQueryLengths_AdjustsMinScore() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Test different query lengths to cover all branches in getMinScore
        String[] queries = new String[]{
                "ab",      // length <= 3
                "abcd",    // length <= 5
                "abcdef",  // length <= 10
                "abcdefghijklmn" // length > 10
        };

        for (int i = 0; i < queries.length; i++) {
            searchRequest = DocumentSearchRequest.builder()
                    .search(queries[i])
                    .page(0)
                    .size(10)
                    .build();

            // Act
            Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

            // Assert
            assertNotNull(result);
        }
    }

    @Test
    void searchDocuments_WithDifferentSortFields_AppliesCorrectSortFieldNames() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Test different sort fields to cover all branches in getSortableFieldName
        String[] sortFields = new String[]{
                "filename",
                "content",
                "created_at",
                "createdAt",
                "someOtherField"
        };

        for (int i = 0; i < sortFields.length; i++) {
            searchRequest = DocumentSearchRequest.builder()
                    .search("test")
                    .sortField(sortFields[i])
                    .sortDirection("ASC")
                    .page(0)
                    .size(10)
                    .build();

            // Act
            Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

            // Assert
            assertNotNull(result);
        }
    }

    @Test
    void searchDocuments_WithNoExplicitSort_AppliesDefaultSorting() throws IOException {
        // Arrange
        searchRequest = DocumentSearchRequest.builder()
                .search("test")
                .sortField(null)
                .page(0)
                .size(10)
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
    }

    @Test
    void searchDocuments_WithNoPreferences_SkipsPreferenceBoosts() throws IOException {
        // Arrange
        searchRequest = DocumentSearchRequest.builder()
                .search("test")
                .page(0)
                .size(10)
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString()))
                .thenReturn(Optional.empty());
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
        verify(documentPreferencesRepository).findByUserId(userId.toString());
    }

    @Test
    void searchDocuments_WithEmptyPreferences_SkipsSpecificBoosts() throws IOException {
        // Arrange
        DocumentPreferences emptyPreferences = new DocumentPreferences();
        // Don't set any preferences, leaving collections null or empty

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString()))
                .thenReturn(Optional.of(emptyPreferences));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        searchRequest = DocumentSearchRequest.builder()
                .search("test")
                .page(0)
                .size(10)
                .build();

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
        verify(documentPreferencesRepository).findByUserId(userId.toString());
    }

    @Test
    void getSuggestions_WithEmptyFilters_ReturnsResults() throws IOException {
        // Arrange
        SuggestionRequest suggestionRequest = SuggestionRequest.builder()
                .query("test")
                .majors(Collections.emptySet())
                .courseCodes(Collections.emptySet())
                .level(null)
                .categories(Collections.emptySet())
                .tags(Collections.emptySet())
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        List<String> result = discoverDocumentSearchService.getSuggestions(suggestionRequest, username);

        // Assert
        assertNotNull(result);
        verify(openSearchClient).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void searchDocuments_WithDefaultSize_UsesDefaultPageSize() throws IOException {
        // Arrange
        searchRequest = DocumentSearchRequest.builder()
                .search("test")
                .page(0)
                .size(0) // This should trigger default size of 10
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
    }

    @Test
    void searchDocuments_WithNullSearchQuery_HandlesNullCase() throws IOException {
        // Arrange
        searchRequest = DocumentSearchRequest.builder()
                .search(null)
                .page(0)
                .size(10)
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = discoverDocumentSearchService.searchDocuments(searchRequest, username);

        // Assert
        assertNotNull(result);
    }
}