package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.RoleResponse;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.DocumentStatus;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.UserDocumentActionType;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    private MockedStatic<CompletableFuture> mockedCompletableFuture;

    @BeforeEach
    void setUp() {
        // Create test user
        userResponse = new UserResponse(
                USER_ID,
                USERNAME,
                "test@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER)
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

        // Mock CompletableFuture.runAsync to run synchronously
        mockedCompletableFuture = mockStatic(CompletableFuture.class);
        mockedCompletableFuture.when(() -> CompletableFuture.runAsync(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });

        // Mock user response
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.ok(userResponse));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (mockedCompletableFuture != null) {
            mockedCompletableFuture.close();
        }
    }

    @Test
    void recommendDocument_Success_Recommend() {
        // Arrange
        when(documentRepository.findAccessibleDocumentByIdAndUserId(DOCUMENT_ID, USER_ID.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(recommendationRepository.findByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(recommendationRepository.save(any(DocumentRecommendation.class))).thenReturn(recommendation);
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(documentInformation);

        // Act
        boolean result = recommendationService.recommendDocument(DOCUMENT_ID, true, USERNAME);

        // Assert
        assertTrue(result);
        verify(recommendationRepository).save(any(DocumentRecommendation.class));

        // Verify document's recommendation count is updated
        ArgumentCaptor<DocumentInformation> docCaptor = ArgumentCaptor.forClass(DocumentInformation.class);
        verify(documentRepository).save(docCaptor.capture());
        DocumentInformation savedDoc = docCaptor.getValue();
        assertEquals(1, savedDoc.getRecommendationCount());

        // Verify history is recorded
        ArgumentCaptor<DocumentUserHistory> historyCaptor = ArgumentCaptor.forClass(DocumentUserHistory.class);
        verify(documentUserHistoryRepository).save(historyCaptor.capture());
        DocumentUserHistory history = historyCaptor.getValue();
        assertEquals(USER_ID.toString(), history.getUserId());
        assertEquals(DOCUMENT_ID, history.getDocumentId());
        assertEquals(UserDocumentActionType.RECOMMENDATION, history.getUserDocumentActionType());
        assertEquals("ADD", history.getDetail());

        // Verify sync event is sent
        verify(publishEventService).sendSyncEvent(any(SyncEventRequest.class));
    }

    @Test
    void recommendDocument_Success_Unrecommend() {
        // Arrange
        documentInformation.setRecommendationCount(1);
        when(documentRepository.findAccessibleDocumentByIdAndUserId(DOCUMENT_ID, USER_ID.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(recommendationRepository.findByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(recommendation));
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(documentInformation);

        // Act
        boolean result = recommendationService.recommendDocument(DOCUMENT_ID, false, USERNAME);

        // Assert
        assertTrue(result);
        verify(recommendationRepository).delete(recommendation);

        // Verify document's recommendation count is updated
        ArgumentCaptor<DocumentInformation> docCaptor = ArgumentCaptor.forClass(DocumentInformation.class);
        verify(documentRepository).save(docCaptor.capture());
        DocumentInformation savedDoc = docCaptor.getValue();
        assertEquals(0, savedDoc.getRecommendationCount());

        // Verify history is recorded
        ArgumentCaptor<DocumentUserHistory> historyCaptor = ArgumentCaptor.forClass(DocumentUserHistory.class);
        verify(documentUserHistoryRepository).save(historyCaptor.capture());
        DocumentUserHistory history = historyCaptor.getValue();
        assertEquals(USER_ID.toString(), history.getUserId());
        assertEquals(DOCUMENT_ID, history.getDocumentId());
        assertEquals(UserDocumentActionType.RECOMMENDATION, history.getUserDocumentActionType());
        assertEquals("REMOVE", history.getDetail());

        // Verify sync event is sent
        verify(publishEventService).sendSyncEvent(any(SyncEventRequest.class));
    }

    @Test
    void recommendDocument_AlreadyRecommended_ReturnsFalse() {
        // Arrange
        when(documentRepository.findAccessibleDocumentByIdAndUserId(DOCUMENT_ID, USER_ID.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(recommendationRepository.findByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(recommendation));

        // Act
        boolean result = recommendationService.recommendDocument(DOCUMENT_ID, true, USERNAME);

        // Assert
        assertFalse(result);
        verify(recommendationRepository, never()).save(any(DocumentRecommendation.class));
        verify(documentRepository, never()).save(any(DocumentInformation.class));
        verify(documentUserHistoryRepository, never()).save(any(DocumentUserHistory.class));
        verify(publishEventService, never()).sendSyncEvent(any(SyncEventRequest.class));
    }

    @Test
    void recommendDocument_NotRecommendedForUnrecommend_ReturnsFalse() {
        // Arrange
        when(documentRepository.findAccessibleDocumentByIdAndUserId(DOCUMENT_ID, USER_ID.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(recommendationRepository.findByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.empty());

        // Act
        boolean result = recommendationService.recommendDocument(DOCUMENT_ID, false, USERNAME);

        // Assert
        assertFalse(result);
        verify(recommendationRepository, never()).delete(any(DocumentRecommendation.class));
        verify(documentRepository, never()).save(any(DocumentInformation.class));
        verify(documentUserHistoryRepository, never()).save(any(DocumentUserHistory.class));
        verify(publishEventService, never()).sendSyncEvent(any(SyncEventRequest.class));
    }

    @Test
    void recommendDocument_DocumentNotFound_ThrowsException() {
        // Arrange
        when(documentRepository.findAccessibleDocumentByIdAndUserId(DOCUMENT_ID, USER_ID.toString()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidDocumentException.class,
                () -> recommendationService.recommendDocument(DOCUMENT_ID, true, USERNAME));
        verify(recommendationRepository, never()).save(any(DocumentRecommendation.class));
        verify(recommendationRepository, never()).delete(any(DocumentRecommendation.class));
        verify(documentRepository, never()).save(any(DocumentInformation.class));
        verify(documentUserHistoryRepository, never()).save(any(DocumentUserHistory.class));
        verify(publishEventService, never()).sendSyncEvent(any(SyncEventRequest.class));
    }

    @Test
    void recommendDocument_UserNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class,
                () -> recommendationService.recommendDocument(DOCUMENT_ID, true, USERNAME));
        verify(recommendationRepository, never()).save(any(DocumentRecommendation.class));
        verify(recommendationRepository, never()).delete(any(DocumentRecommendation.class));
        verify(documentRepository, never()).save(any(DocumentInformation.class));
        verify(documentUserHistoryRepository, never()).save(any(DocumentUserHistory.class));
        verify(publishEventService, never()).sendSyncEvent(any(SyncEventRequest.class));
    }

    @Test
    void isDocumentRecommendedByUser_RecommendedDocument_ReturnsTrue() {
        // Arrange
        when(recommendationRepository.existsByDocumentIdAndMentorId(DOCUMENT_ID, USER_ID)).thenReturn(true);

        // Act
        boolean result = recommendationService.isDocumentRecommendedByUser(DOCUMENT_ID, USERNAME);

        // Assert
        assertTrue(result);
    }

    @Test
    void isDocumentRecommendedByUser_NotRecommendedDocument_ReturnsFalse() {
        // Arrange
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
        verify(recommendationRepository, never()).existsByDocumentIdAndMentorId(anyString(), any(UUID.class));
    }

    @Test
    void isDocumentRecommendedByUser_NullUserResponse_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(USERNAME)).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class,
                () -> recommendationService.isDocumentRecommendedByUser(DOCUMENT_ID, USERNAME));
        verify(recommendationRepository, never()).existsByDocumentIdAndMentorId(anyString(), any(UUID.class));
    }
}