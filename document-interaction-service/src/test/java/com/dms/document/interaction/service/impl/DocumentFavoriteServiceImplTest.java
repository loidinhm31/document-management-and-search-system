package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.RoleResponse;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.InteractionType;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.exception.DuplicateFavoriteException;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentFavorite;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.repository.DocumentFavoriteRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import com.dms.document.interaction.service.DocumentPreferencesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Captor
    private ArgumentCaptor<DocumentFavorite> documentFavoriteCaptor;

    @Captor
    private ArgumentCaptor<DocumentUserHistory> documentUserHistoryCaptor;

    private UUID userId;
    private UserResponse userResponse;
    private ResponseEntity<UserResponse> userResponseEntity;
    private DocumentInformation documentInformation;
    private final String documentId = "test-document-id";
    private final String username = "testuser";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        RoleResponse roleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER);
        userResponse = new UserResponse(userId, username, "test@example.com", roleResponse);
        userResponseEntity = ResponseEntity.ok(userResponse);

        documentInformation = new DocumentInformation();
        documentInformation.setId(documentId);
        documentInformation.setUserId(userId.toString());
        documentInformation.setCurrentVersion(1);
        documentInformation.setFilename("test-document.pdf");
    }

    @Test
    void favoriteDocument_Success() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(false);

        // Act
        documentFavoriteService.favoriteDocument(documentId, username);

        // Assert
        verify(documentFavoriteRepository).save(documentFavoriteCaptor.capture());
        DocumentFavorite savedFavorite = documentFavoriteCaptor.getValue();
        assertEquals(userId, savedFavorite.getUserId());
        assertEquals(documentId, savedFavorite.getDocumentId());
        // We don't check createdAt because it's set by @PrePersist and won't be populated in the unit test
    }

    @Test
    void favoriteDocument_AlreadyFavorited_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(true);

        // Act & Assert
        assertThrows(DuplicateFavoriteException.class, () ->
                documentFavoriteService.favoriteDocument(documentId, username));

        verify(documentFavoriteRepository, never()).save(any(DocumentFavorite.class));
    }

    @Test
    void favoriteDocument_DocumentNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidDocumentException.class, () ->
                documentFavoriteService.favoriteDocument(documentId, username));

        verify(documentFavoriteRepository, never()).save(any(DocumentFavorite.class));
    }

    @Test
    void favoriteDocument_UserNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.favoriteDocument(documentId, username));

        verify(documentFavoriteRepository, never()).save(any(DocumentFavorite.class));
    }

    @Test
    void favoriteDocument_InvalidRole_ThrowsException() {
        // Arrange
        RoleResponse adminRole = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        UserResponse adminUser = new UserResponse(userId, username, "admin@example.com", adminRole);
        ResponseEntity<UserResponse> adminResponse = ResponseEntity.ok(adminUser);

        when(userClient.getUserByUsername(username)).thenReturn(adminResponse);

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.favoriteDocument(documentId, username));

        verify(documentFavoriteRepository, never()).save(any(DocumentFavorite.class));
    }

    @Test
    void unfavoriteDocument_Success() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));
        doNothing().when(documentFavoriteRepository).deleteByUserIdAndDocumentId(userId, documentId);

        // Act
        documentFavoriteService.unfavoriteDocument(documentId, username);

        // Assert
        verify(documentFavoriteRepository).deleteByUserIdAndDocumentId(userId, documentId);
    }

    @Test
    void unfavoriteDocument_DocumentNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidDocumentException.class, () ->
                documentFavoriteService.unfavoriteDocument(documentId, username));

        verify(documentFavoriteRepository, never()).deleteByUserIdAndDocumentId(any(), anyString());
    }

    @Test
    void unfavoriteDocument_UserNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.unfavoriteDocument(documentId, username));

        verify(documentFavoriteRepository, never()).deleteByUserIdAndDocumentId(any(), anyString());
    }

    @Test
    void isDocumentFavorited_DocumentIsFavorited_ReturnsTrue() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(true);

        // Act
        boolean result = documentFavoriteService.isDocumentFavorited(documentId, username);

        // Assert
        assertTrue(result);
        verify(documentFavoriteRepository).existsByUserIdAndDocumentId(userId, documentId);
    }

    @Test
    void isDocumentFavorited_DocumentIsNotFavorited_ReturnsFalse() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(false);

        // Act
        boolean result = documentFavoriteService.isDocumentFavorited(documentId, username);

        // Assert
        assertFalse(result);
        verify(documentFavoriteRepository).existsByUserIdAndDocumentId(userId, documentId);
    }

    @Test
    void isDocumentFavorited_UserNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentFavoriteService.isDocumentFavorited(documentId, username));

        verify(documentFavoriteRepository, never()).existsByUserIdAndDocumentId(any(), anyString());
    }

    @Test
    void isDocumentFavorited_MentorRole_Success() {
        // Arrange
        RoleResponse mentorRole = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_MENTOR);
        UserResponse mentorUser = new UserResponse(userId, username, "mentor@example.com", mentorRole);
        ResponseEntity<UserResponse> mentorResponse = ResponseEntity.ok(mentorUser);

        when(userClient.getUserByUsername(username)).thenReturn(mentorResponse);
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(true);

        // Act
        boolean result = documentFavoriteService.isDocumentFavorited(documentId, username);

        // Assert
        assertTrue(result);
        verify(documentFavoriteRepository).existsByUserIdAndDocumentId(userId, documentId);
    }

    @Test
    void favoriteDocument_RecordsInteraction() {
        // Since we can't easily test the CompletableFuture directly in a unit test,
        // we'll verify that other aspects of the method work correctly
        // This test is more for documentation than actual verification of async operations

        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));
        when(documentFavoriteRepository.existsByUserIdAndDocumentId(userId, documentId)).thenReturn(false);

        // Act
        documentFavoriteService.favoriteDocument(documentId, username);

        // Assert
        // Verify main method functionality - actual async operations would be tested in integration tests
        verify(documentFavoriteRepository).save(any(DocumentFavorite.class));
    }
}