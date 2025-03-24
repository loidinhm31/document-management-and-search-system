package com.dms.document.search.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.DocumentResponseDto;
import com.dms.document.search.dto.RoleResponse;
import com.dms.document.search.dto.UserResponse;
import com.dms.document.search.enums.AppRole;
import com.dms.document.search.exception.InvalidDocumentException;
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
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class DocumentRecommendationServiceImplTest {

    @Mock
    private RestHighLevelClient openSearchClient;

    @Mock
    private UserClient userClient;

    @Mock
    private DocumentPreferencesRepository documentPreferencesRepository;

    @Mock
    private DocumentFavoriteService documentFavoriteService;

    @InjectMocks
    private DocumentRecommendationServiceImpl documentRecommendationService;

    private UUID userId;
    private String username;
    private UserResponse userResponse;
    private PageRequest pageable;
    private DocumentPreferences preferences;
    private SearchResponse searchResponse;
    private GetResponse getResponse;

    @BeforeEach
    void setUp() throws IOException {
        userId = UUID.randomUUID();
        username = "testuser";
        userResponse = new UserResponse(userId, username, "test@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER));
        pageable = PageRequest.of(0, 10);

        // Setup preferences
        preferences = new DocumentPreferences();
        preferences.setUserId(userId.toString());
        preferences.setCreatedAt(Instant.now());
        preferences.setUpdatedAt(Instant.now());

        // Mock search hit
        SearchHit hit = mock(SearchHit.class);
        lenient().when(hit.getId()).thenReturn("doc1");
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("filename", "Test Document");
        sourceMap.put("userId", userId.toString());
        sourceMap.put("createdAt", new Date().toString());
        lenient().when(hit.getSourceAsMap()).thenReturn(sourceMap);

        // Mock search hits
        SearchHits searchHits = mock(SearchHits.class);
        lenient().when(searchHits.getHits()).thenReturn(new SearchHit[]{hit});
        lenient().when(searchHits.getTotalHits()).thenReturn(new TotalHits(1L, TotalHits.Relation.EQUAL_TO));

        // Mock search response
        searchResponse = mock(SearchResponse.class);
        lenient().when(searchResponse.getHits()).thenReturn(searchHits);

        // Mock get response for document
        getResponse = mock(GetResponse.class);
        lenient().when(getResponse.isExists()).thenReturn(true);
        Map<String, Object> documentSource = new HashMap<>();
        documentSource.put("filename", "Source Document");
        documentSource.put("content", "This is the content of the source document");
        documentSource.put("majors", List.of("Computer Science"));
        documentSource.put("categories", List.of("Assignment"));
        lenient().when(getResponse.getSourceAsMap()).thenReturn(documentSource);
    }

    @Test
    void getRecommendations_WithDocumentId_ReturnsContentBasedRecommendations() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString())).thenReturn(Optional.of(preferences));
        when(openSearchClient.get(any(GetRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(getResponse);
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = documentRecommendationService.getRecommendations("doc1", false, username, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("doc1", result.getContent().get(0).getId());
        verify(userClient, times(1)).getUserByUsername(username);
        verify(openSearchClient, times(1)).get(any(GetRequest.class), eq(RequestOptions.DEFAULT));
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void getRecommendations_WithNonExistentDocumentId_ThrowsException() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString())).thenReturn(Optional.of(preferences));

        GetResponse nonExistentResponse = mock(GetResponse.class);
        when(nonExistentResponse.isExists()).thenReturn(false);
        when(openSearchClient.get(any(GetRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(nonExistentResponse);

        // Act & Assert
        assertThrows(InvalidDocumentException.class, () -> {
            documentRecommendationService.getRecommendations("non-existent", false, username, pageable);
        });
        verify(userClient, times(1)).getUserByUsername(username);
        verify(openSearchClient, times(1)).get(any(GetRequest.class), eq(RequestOptions.DEFAULT));
        verify(openSearchClient, never()).search(any(SearchRequest.class), any(RequestOptions.class));
    }

    @Test
    void getRecommendations_WithoutDocumentId_ReturnsPreferenceBasedRecommendations() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString())).thenReturn(Optional.of(preferences));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = documentRecommendationService.getRecommendations(null, false, username, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("doc1", result.getContent().get(0).getId());
        verify(userClient, times(1)).getUserByUsername(username);
        verify(openSearchClient, never()).get(any(GetRequest.class), any(RequestOptions.class));
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void getRecommendations_WithFavoriteOnly_AppliesFavoriteFilter() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString())).thenReturn(Optional.of(preferences));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(searchResponse);
        doNothing().when(documentFavoriteService).addFavoriteFilter(any(), any(UUID.class));

        // Act
        Page<DocumentResponseDto> result = documentRecommendationService.getRecommendations(null, true, username, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(documentFavoriteService, times(1)).addFavoriteFilter(any(), eq(userId));
    }

    @Test
    void getRecommendations_UserNotFound_ThrowsException() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.notFound().build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () -> {
            documentRecommendationService.getRecommendations(null, false, username, pageable);
        });
        verify(userClient, times(1)).getUserByUsername(username);
        verify(documentPreferencesRepository, never()).findByUserId(anyString());
        verify(openSearchClient, never()).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void getRecommendations_SearchError_ThrowsException() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString())).thenReturn(Optional.of(preferences));
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT)))
                .thenThrow(new IOException("Search error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            documentRecommendationService.getRecommendations(null, false, username, pageable);
        });
        verify(userClient, times(1)).getUserByUsername(username);
        verify(documentPreferencesRepository, times(1)).findByUserId(userId.toString());
        verify(openSearchClient, times(1)).search(any(SearchRequest.class), eq(RequestOptions.DEFAULT));
    }

    @Test
    void getRecommendations_NoExistingPreferences_CreatesNewPreferences() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString())).thenReturn(Optional.empty());
        when(documentPreferencesRepository.save(any(DocumentPreferences.class))).thenReturn(preferences);
        when(openSearchClient.search(any(SearchRequest.class), eq(RequestOptions.DEFAULT))).thenReturn(searchResponse);

        // Act
        Page<DocumentResponseDto> result = documentRecommendationService.getRecommendations(null, false, username, pageable);

        // Assert
        assertNotNull(result);
        verify(documentPreferencesRepository, times(1)).findByUserId(userId.toString());
        verify(documentPreferencesRepository, times(1)).save(any(DocumentPreferences.class));
    }
}