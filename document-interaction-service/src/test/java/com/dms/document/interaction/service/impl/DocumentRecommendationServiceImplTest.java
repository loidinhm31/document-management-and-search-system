package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.RoleResponse;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.DocumentStatus;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentRecommendation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.repository.DocumentRecommendationRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import com.dms.document.interaction.service.PublishEventService;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentRecommendationServiceImplTest {

    @Mock
    private DocumentRecommendationRepository recommendationRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private DocumentUserHistoryRepository documentUserHistoryRepository;

    @Mock
    private PublishEventService publishEventService;

    @InjectMocks
    private DocumentRecommendationServiceImpl recommendationService;

    private final String USERNAME = "testuser";
    private final UUID USER_ID = UUID.randomUUID();
    private final String DOCUMENT_ID = "doc123";
    private UserResponse userResponse;
    private DocumentInformation documentInformation;
    private DocumentRecommendation recommendation;

    @BeforeEach
    void setUp() {
        // Create test user
        userResponse = new UserResponse(
                USER_ID,
                USERNAME,
                "test@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.ROLE_MENTOR)
        );

        // Create test document
        documentInformation = new DocumentInformation();
        documentInformation.setId(DOCUMENT_ID);
        documentInformation.setUserId(UUID.randomUUID().toString());
        documentInformation.setStatus(DocumentStatus.COMPLETED);
        documentInformation.setCurrentVersion(1);
        documentInformation.setRecommendationCount(0);

        // Create test recommendation
        recommendation = new DocumentRecommendation();
        recommendation.setDocumentId(DOCUMENT_ID);
        recommendation.setMentorId(USER_ID);
        recommendation.setCreatedAt(Instant.now());
    }

    @Test
    void recommendDocument_Success() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(DOCUMENT_ID, USER_ID.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(recommendationRepository.existsByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID)).thenReturn(false);
        when(recommendationRepository.countByDocumentId(DOCUMENT_ID)).thenReturn(1L);
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(documentInformation);

        // Act
        boolean result = recommendationService.recommendDocument(DOCUMENT_ID, USERNAME);

        // Assert
        assertTrue(result);
        verify(recommendationRepository).save(any(DocumentRecommendation.class));

        // Verify document's recommendation count is updated
        ArgumentCaptor<DocumentInformation> docCaptor = ArgumentCaptor.forClass(DocumentInformation.class);
        verify(documentRepository).save(docCaptor.capture());
        DocumentInformation savedDoc = docCaptor.getValue();
        assertEquals(1, savedDoc.getRecommendationCount());
    }

    @Test
    void recommendDocument_AlreadyRecommended_ReturnsFalse() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(DOCUMENT_ID, USER_ID.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(recommendationRepository.existsByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID)).thenReturn(true);

        // Act
        boolean result = recommendationService.recommendDocument(DOCUMENT_ID, USERNAME);

        // Assert
        assertFalse(result);
        verify(recommendationRepository, never()).save(any(DocumentRecommendation.class));
        verify(documentRepository, never()).save(any(DocumentInformation.class));
    }

    @Test
    void recommendDocument_DocumentNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(DOCUMENT_ID, USER_ID.toString()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidDocumentException.class,
                () -> recommendationService.recommendDocument(DOCUMENT_ID, USERNAME));
    }

    @Test
    void recommendDocument_UserNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class,
                () -> recommendationService.recommendDocument(DOCUMENT_ID, USERNAME));
    }

    @Test
    void unrecommendDocument_Success() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(documentInformation));
        when(recommendationRepository.findByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(recommendation));
        when(recommendationRepository.countByDocumentId(DOCUMENT_ID)).thenReturn(0L);
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(documentInformation);

        // Act
        boolean result = recommendationService.unrecommendDocument(DOCUMENT_ID, USERNAME);

        // Assert
        assertTrue(result);
        verify(recommendationRepository).delete(recommendation);

        // Verify document's recommendation count is updated
        ArgumentCaptor<DocumentInformation> docCaptor = ArgumentCaptor.forClass(DocumentInformation.class);
        verify(documentRepository).save(docCaptor.capture());
        DocumentInformation savedDoc = docCaptor.getValue();
        assertEquals(0, savedDoc.getRecommendationCount());
    }

    @Test
    void unrecommendDocument_NotRecommended_ReturnsFalse() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(documentInformation));
        when(recommendationRepository.findByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.empty());

        // Act
        boolean result = recommendationService.unrecommendDocument(DOCUMENT_ID, USERNAME);

        // Assert
        assertFalse(result);
        verify(recommendationRepository, never()).delete(any(DocumentRecommendation.class));
        verify(documentRepository, never()).save(any(DocumentInformation.class));
    }

    @Test
    void unrecommendDocument_DocumentNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidDocumentException.class,
                () -> recommendationService.unrecommendDocument(DOCUMENT_ID, USERNAME));
    }

    @Test
    void unrecommendDocument_UserNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class,
                () -> recommendationService.unrecommendDocument(DOCUMENT_ID, USERNAME));
    }

    @Test
    void isDocumentRecommendedByUser_RecommendedDocument_ReturnsTrue() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.ok(userResponse));
        when(recommendationRepository.existsByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID)).thenReturn(true);

        // Act
        boolean result = recommendationService.isDocumentRecommendedByUser(DOCUMENT_ID, USERNAME);

        // Assert
        assertTrue(result);
    }

    @Test
    void isDocumentRecommendedByUser_NotRecommendedDocument_ReturnsFalse() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.ok(userResponse));
        when(recommendationRepository.existsByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID)).thenReturn(false);

        // Act
        boolean result = recommendationService.isDocumentRecommendedByUser(DOCUMENT_ID, USERNAME);

        // Assert
        assertFalse(result);
    }

    @Test
    void isDocumentRecommendedByUser_UserNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class,
                () -> recommendationService.isDocumentRecommendedByUser(DOCUMENT_ID, USERNAME));
    }
}