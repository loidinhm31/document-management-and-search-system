package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.RoleResponse;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.InteractionType;
import com.dms.document.interaction.exception.DuplicateFavoriteException;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentFavorite;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.repository.DocumentFavoriteRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import com.dms.document.interaction.service.DocumentPreferencesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentFavoriteServiceImplTest {

    @Mock
    private DocumentFavoriteRepository documentFavoriteRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private DocumentPreferencesService documentPreferencesService;

    @Mock
    private DocumentUserHistoryRepository documentUserHistoryRepository;

    @InjectMocks
    private DocumentFavoriteServiceImpl documentFavoriteService;

    private final UUID userId = UUID.randomUUID();
    private final String documentId = "doc123";
    private final String username = "testuser";
    private final UserResponse userResponse = new UserResponse(userId, username, "test@example.com",
            new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER));
    private final DocumentInformation documentInformation = DocumentInformation.builder()
            .id(documentId)
            .currentVersion(1)
            .build();

    private MockedStatic<CompletableFuture> mockedCompletableFuture;

    @BeforeEach
    void setUp() {
        ResponseEntity<UserResponse> userResponseEntity = ResponseEntity.ok(userResponse);
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);

        // Mock CompletableFuture.runAsync to run synchronously
        mockedCompletableFuture = mockStatic(CompletableFuture.class);
        mockedCompletableFuture.when(() -> CompletableFuture.runAsync(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (mockedCompletableFuture != null) {
            mockedCompletableFuture.close();
        }
    }

    @Test
    void favoriteDocument_Success() throws Exception {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(false);

        // Act
        documentFavoriteService.favoriteDocument(documentId, username);

        // Assert
        verify(documentFavoriteRepository).save(any(DocumentFavorite.class));
        verify(documentUserHistoryRepository).save(any());
        verify(documentPreferencesService).recordInteraction(userId, documentId, InteractionType.FAVORITE);
    }

    @Test
    void favoriteDocument_UserNotFound() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.notFound().build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.favoriteDocument(documentId, username));
        verify(documentFavoriteRepository, never()).save(any());
    }

    @Test
    void favoriteDocument_InvalidRole() {
        // Arrange
        UserResponse adminUser = new UserResponse(userId, username, "admin@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN));
        ResponseEntity<UserResponse> adminResponse = ResponseEntity.ok(adminUser);
        when(userClient.getUserByUsername(username)).thenReturn(adminResponse);

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.favoriteDocument(documentId, username));
        verify(documentFavoriteRepository, never()).save(any());
    }

    @Test
    void favoriteDocument_DocumentNotFound() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidDocumentException.class, () ->
                documentFavoriteService.favoriteDocument(documentId, username));
        verify(documentFavoriteRepository, never()).save(any());
    }

    @Test
    void favoriteDocument_AlreadyFavorited() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(true);

        // Act & Assert
        assertThrows(DuplicateFavoriteException.class, () ->
                documentFavoriteService.favoriteDocument(documentId, username));
        verify(documentFavoriteRepository, never()).save(any());
    }

    @Test
    void favoriteDocument_WithMentorRole() {
        // Arrange
        UserResponse mentorUser = new UserResponse(userId, username, "mentor@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.ROLE_MENTOR));
        ResponseEntity<UserResponse> mentorResponse = ResponseEntity.ok(mentorUser);
        when(userClient.getUserByUsername(username)).thenReturn(mentorResponse);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(false);

        // Act
        documentFavoriteService.favoriteDocument(documentId, username);

        // Assert
        verify(documentFavoriteRepository).save(any(DocumentFavorite.class));
    }

    @Test
    void unfavoriteDocument_Success() throws Exception {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));

        // Act
        documentFavoriteService.unfavoriteDocument(documentId, username);

        // Assert
        verify(documentFavoriteRepository).deleteByUserIdAndDocumentId(userId, documentId);
        verify(documentUserHistoryRepository).save(any());
    }

    @Test
    void unfavoriteDocument_UserNotFound() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.notFound().build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.unfavoriteDocument(documentId, username));
        verify(documentFavoriteRepository, never()).deleteByUserIdAndDocumentId(any(), anyString());
    }

    @Test
    void unfavoriteDocument_InvalidRole() {
        // Arrange
        UserResponse adminUser = new UserResponse(userId, username, "admin@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN));
        ResponseEntity<UserResponse> adminResponse = ResponseEntity.ok(adminUser);
        when(userClient.getUserByUsername(username)).thenReturn(adminResponse);

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.unfavoriteDocument(documentId, username));
        verify(documentFavoriteRepository, never()).deleteByUserIdAndDocumentId(any(), anyString());
    }

    @Test
    void unfavoriteDocument_DocumentNotFound() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidDocumentException.class, () ->
                documentFavoriteService.unfavoriteDocument(documentId, username));
        verify(documentFavoriteRepository, never()).deleteByUserIdAndDocumentId(any(), anyString());
    }

    @Test
    void unfavoriteDocument_WithMentorRole() {
        // Arrange
        UserResponse mentorUser = new UserResponse(userId, username, "mentor@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.ROLE_MENTOR));
        ResponseEntity<UserResponse> mentorResponse = ResponseEntity.ok(mentorUser);
        when(userClient.getUserByUsername(username)).thenReturn(mentorResponse);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));

        // Act
        documentFavoriteService.unfavoriteDocument(documentId, username);

        // Assert
        verify(documentFavoriteRepository).deleteByUserIdAndDocumentId(userId, documentId);
    }

    @Test
    void checkDocumentFavorited_ReturnsTrueWhenFavorited() {
        // Arrange
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(true);

        // Act
        boolean result = documentFavoriteService.checkDocumentFavorited(documentId, username);

        // Assert
        assertTrue(result);
    }

    @Test
    void checkDocumentFavorited_ReturnsFalseWhenNotFavorited() {
        // Arrange
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(false);

        // Act
        boolean result = documentFavoriteService.checkDocumentFavorited(documentId, username);

        // Assert
        assertFalse(result);
    }

    @Test
    void checkDocumentFavorited_UserNotFound() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.notFound().build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.checkDocumentFavorited(documentId, username));
        verify(documentFavoriteRepository, never()).existsByUserIdAndDocumentId(any(), anyString());
    }

    @Test
    void checkDocumentFavorited_InvalidRole() {
        // Arrange
        UserResponse adminUser = new UserResponse(userId, username, "admin@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN));
        ResponseEntity<UserResponse> adminResponse = ResponseEntity.ok(adminUser);
        when(userClient.getUserByUsername(username)).thenReturn(adminResponse);

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.checkDocumentFavorited(documentId, username));
        verify(documentFavoriteRepository, never()).existsByUserIdAndDocumentId(any(), anyString());
    }

    @Test
    void checkDocumentFavorited_ClientConnectionError() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.checkDocumentFavorited(documentId, username));
    }

    @Test
    void checkDocumentFavorited_NullUserResponse() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.checkDocumentFavorited(documentId, username));
    }
}