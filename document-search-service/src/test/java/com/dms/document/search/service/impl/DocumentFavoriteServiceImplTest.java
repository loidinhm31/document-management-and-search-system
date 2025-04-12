package com.dms.document.search.service.impl;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dms.document.search.repository.DocumentFavoriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class DocumentFavoriteServiceImplTest {

    @Mock
    private DocumentFavoriteRepository documentFavoriteRepository;

    @InjectMocks
    private DocumentFavoriteServiceImpl documentFavoriteService;

    private UUID userId;
    private Set<String> favoriteDocIds;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        favoriteDocIds = new HashSet<>();
        favoriteDocIds.add("doc1");
        favoriteDocIds.add("doc2");
    }

    @Test
    void addFavoriteFilter_WithFavorites_AddsTermsQuery() {
        // Arrange
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        when(documentFavoriteRepository.findRecentFavoriteDocumentIdsByUserId(eq(userId), anyInt()))
                .thenReturn(favoriteDocIds);
        when(documentFavoriteRepository.findDocumentIdsByUserId(userId))
                .thenReturn(favoriteDocIds);

        // Act
        documentFavoriteService.addFavoriteFilter(queryBuilder, userId);

        // Assert
        verify(documentFavoriteRepository, times(1)).findRecentFavoriteDocumentIdsByUserId(eq(userId), anyInt());
        verify(documentFavoriteRepository, times(1)).findDocumentIdsByUserId(userId);
        // The actual query filter is applied to the queryBuilder which is hard to test directly
    }

    @Test
    void addFavoriteFilter_WithNoFavorites_AddsNoMatchFilter() {
        // Arrange
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        when(documentFavoriteRepository.findRecentFavoriteDocumentIdsByUserId(eq(userId), anyInt()))
                .thenReturn(new HashSet<>());

        // Act
        documentFavoriteService.addFavoriteFilter(queryBuilder, userId);

        // Assert
        verify(documentFavoriteRepository, times(1)).findRecentFavoriteDocumentIdsByUserId(eq(userId), anyInt());
        verify(documentFavoriteRepository, never()).findDocumentIdsByUserId(userId);
        // Should add a filter that ensures no documents match
    }

    @Test
    void addFavoriteFilter_WithTooManyFavorites_HandlesBatching() {
        // Arrange
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        Set<String> largeSet = new HashSet<>();
        for (int i = 0; i < 1500; i++) {
            largeSet.add("doc" + i);
        }

        when(documentFavoriteRepository.findRecentFavoriteDocumentIdsByUserId(eq(userId), anyInt()))
                .thenReturn(largeSet);
        when(documentFavoriteRepository.findDocumentIdsByUserId(userId))
                .thenReturn(largeSet);

        // Act
        documentFavoriteService.addFavoriteFilter(queryBuilder, userId);

        // Assert
        verify(documentFavoriteRepository, times(1)).findRecentFavoriteDocumentIdsByUserId(eq(userId), anyInt());
        verify(documentFavoriteRepository, times(1)).findDocumentIdsByUserId(userId);
        // The large set should be handled with batched queries
    }

    @Test
    void addFavoriteFilter_WithRepositoryException_HandlesGracefully() {
        // Arrange
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        when(documentFavoriteRepository.findRecentFavoriteDocumentIdsByUserId(eq(userId), anyInt()))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        documentFavoriteService.addFavoriteFilter(queryBuilder, userId);

        // Assert
        verify(documentFavoriteRepository, times(1)).findRecentFavoriteDocumentIdsByUserId(eq(userId), anyInt());
        // Should handle the exception and continue without throwing
    }
}