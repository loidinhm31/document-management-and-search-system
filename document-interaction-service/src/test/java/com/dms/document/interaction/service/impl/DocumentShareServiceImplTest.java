package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.ShareSettings;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.dto.UpdateShareSettingsRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.dto.RoleResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.SharingType;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import com.dms.document.interaction.service.DocumentPreferencesService;
import com.dms.document.interaction.service.PublishEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentShareServiceImplTest {

    @Mock
    private UserClient userClient;

    @Mock
    private PublishEventService publishEventService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentPreferencesService documentPreferencesService;

    @Mock
    private DocumentUserHistoryRepository documentUserHistoryRepository;

    @Captor
    private ArgumentCaptor<DocumentInformation> documentCaptor;

    @Captor
    private ArgumentCaptor<DocumentUserHistory> historyCaptor;

    @Captor
    private ArgumentCaptor<SyncEventRequest> syncEventCaptor;

    @InjectMocks
    private DocumentShareServiceImpl documentShareService;

    private String documentId;
    private String username;
    private UUID userId;
    private UserResponse userResponse;
    private DocumentInformation documentInformation;

    @BeforeEach
    void setUp() {
        documentId = "doc123";
        username = "testuser";
        userId = UUID.randomUUID();

        // Create user response
        RoleResponse roleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER);
        userResponse = new UserResponse(userId, username, "test@example.com", roleResponse);

        // Create document
        documentInformation = DocumentInformation.builder()
                .id(documentId)
                .userId(userId.toString())
                .sharingType(SharingType.PRIVATE)
                .sharedWith(new HashSet<>())
                .currentVersion(1)
                .build();
    }

    @Test
    void getDocumentShareSettings_ShouldReturnSettings_WhenDocumentExists() {
        // Arrange
        ResponseEntity<UserResponse> userResponseEntity = new ResponseEntity<>(userResponse, HttpStatus.OK);
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findByIdAndUserId(documentId, userId.toString()))
                .thenReturn(Optional.of(documentInformation));

        // Act
        ShareSettings result = documentShareService.getDocumentShareSettings(documentId, username);

        // Assert
        assertNotNull(result);
        assertFalse(result.isPublic());
        assertTrue(result.sharedWith().isEmpty());
        verify(documentRepository).findByIdAndUserId(documentId, userId.toString());
    }

    @Test
    void getDocumentShareSettings_ShouldThrowException_WhenUserNotFound() {
        // Arrange
        ResponseEntity<UserResponse> userResponseEntity = new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentShareService.getDocumentShareSettings(documentId, username));
    }

    @Test
    void getDocumentShareSettings_ShouldThrowException_WhenDocumentNotFound() {
        // Arrange
        ResponseEntity<UserResponse> userResponseEntity = new ResponseEntity<>(userResponse, HttpStatus.OK);
        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findByIdAndUserId(documentId, userId.toString()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidDocumentException.class, () ->
                documentShareService.getDocumentShareSettings(documentId, username));
    }

    @Test
    void updateDocumentShareSettings_ShouldUpdateToPublic_WhenRequestIsPublic() {
        // Arrange
        UpdateShareSettingsRequest request = new UpdateShareSettingsRequest(true, new HashSet<>());
        ResponseEntity<UserResponse> userResponseEntity = new ResponseEntity<>(userResponse, HttpStatus.OK);

        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findByIdAndUserId(documentId, userId.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(documentInformation);

        // Mock CompletableFuture to execute synchronously
        try (MockedStatic<CompletableFuture> mockedCompletableFuture = Mockito.mockStatic(CompletableFuture.class)) {
            // Make CompletableFuture.runAsync run synchronously
            mockedCompletableFuture.when(() -> CompletableFuture.runAsync(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run();
                        return CompletableFuture.completedFuture(null);
                    });

            // Act
            DocumentInformation result = documentShareService.updateDocumentShareSettings(documentId, request, username);

            // Assert
            assertNotNull(result);
            verify(documentRepository).save(documentCaptor.capture());
            DocumentInformation capturedDoc = documentCaptor.getValue();

            assertEquals(SharingType.PUBLIC, capturedDoc.getSharingType());
            assertTrue(capturedDoc.getSharedWith().isEmpty());
            assertNotNull(capturedDoc.getUpdatedAt());
            assertEquals(username, capturedDoc.getUpdatedBy());

            // Verify the async calls were made
            verify(documentUserHistoryRepository).save(any(DocumentUserHistory.class));
            verify(publishEventService).sendSyncEvent(any(SyncEventRequest.class));
            verify(documentPreferencesService).recordInteraction(any(UUID.class), anyString(), any());
        }
    }

    @Test
    void updateDocumentShareSettings_ShouldUpdateToSpecific_WhenSharedWithNotEmpty() {
        // Arrange
        Set<UUID> sharedWith = new HashSet<>(Arrays.asList(UUID.randomUUID(), UUID.randomUUID()));
        UpdateShareSettingsRequest request = new UpdateShareSettingsRequest(false, sharedWith);
        ResponseEntity<UserResponse> userResponseEntity = new ResponseEntity<>(userResponse, HttpStatus.OK);
        List<UserResponse> sharedUsers = new ArrayList<>();
        // Add one user for each UUID in sharedWith to ensure validation passes
        for (UUID id : sharedWith) {
            sharedUsers.add(new UserResponse(id, "user-" + id, "user@example.com", null));
        }

        // Mock CompletableFuture to execute sync instead of async
        MockedStatic<CompletableFuture> mockedCompletableFuture = Mockito.mockStatic(CompletableFuture.class);

        try {
            // Mock CompletableFuture.runAsync to run synchronously
            mockedCompletableFuture.when(() -> CompletableFuture.runAsync(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run();
                        return CompletableFuture.completedFuture(null);
                    });

            when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
            when(documentRepository.findByIdAndUserId(documentId, userId.toString()))
                    .thenReturn(Optional.of(documentInformation));
            when(documentRepository.save(any(DocumentInformation.class))).thenReturn(documentInformation);
            when(userClient.getUsersByIds(anyList())).thenReturn(ResponseEntity.ok(sharedUsers));
            when(documentUserHistoryRepository.save(any(DocumentUserHistory.class)))
                    .thenReturn(DocumentUserHistory.builder().build());

            // Act
            DocumentInformation result = documentShareService.updateDocumentShareSettings(documentId, request, username);

            // Assert
            assertNotNull(result);
            verify(documentRepository).save(documentCaptor.capture());
            DocumentInformation capturedDoc = documentCaptor.getValue();

            assertEquals(SharingType.SPECIFIC, capturedDoc.getSharingType());
            assertEquals(sharedWith.size(), capturedDoc.getSharedWith().size());
            // Verify shared IDs are converted to strings
            assertTrue(capturedDoc.getSharedWith().containsAll(
                    sharedWith.stream().map(UUID::toString).toList()
            ));

            // Verify that the async operations were called
            verify(documentUserHistoryRepository).save(any(DocumentUserHistory.class));
            verify(publishEventService).sendSyncEvent(any(SyncEventRequest.class));
            verify(documentPreferencesService).recordInteraction(eq(userId), eq(documentId), any());
        } finally {
            mockedCompletableFuture.close();
        }
    }

    @Test
    void updateDocumentShareSettings_ShouldUpdateToPrivate_WhenNotPublicAndSharedWithEmpty() {
        // Arrange
        UpdateShareSettingsRequest request = new UpdateShareSettingsRequest(false, new HashSet<>());
        ResponseEntity<UserResponse> userResponseEntity = new ResponseEntity<>(userResponse, HttpStatus.OK);

        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findByIdAndUserId(documentId, userId.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(documentInformation);

        // Mock asynchronous calls
        when(documentUserHistoryRepository.save(any(DocumentUserHistory.class)))
                .thenReturn(DocumentUserHistory.builder().build());
        doNothing().when(publishEventService).sendSyncEvent(any(SyncEventRequest.class));

        // Act
        DocumentInformation result = documentShareService.updateDocumentShareSettings(documentId, request, username);

        // Assert
        assertNotNull(result);
        verify(documentRepository).save(documentCaptor.capture());
        DocumentInformation capturedDoc = documentCaptor.getValue();

        assertEquals(SharingType.PRIVATE, capturedDoc.getSharingType());
        assertTrue(capturedDoc.getSharedWith().isEmpty());
    }

    @Test
    void updateDocumentShareSettings_ShouldUpdateHistory_AndSendSyncEvent() {
        // Arrange
        UpdateShareSettingsRequest request = new UpdateShareSettingsRequest(true, new HashSet<>());
        ResponseEntity<UserResponse> userResponseEntity = new ResponseEntity<>(userResponse, HttpStatus.OK);

        // Mock static CompletableFuture to execute synchronously
        MockedStatic<CompletableFuture> mockedCompletableFuture = Mockito.mockStatic(CompletableFuture.class);

        try {
            // Make CompletableFuture.runAsync run synchronously
            mockedCompletableFuture.when(() -> CompletableFuture.runAsync(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run();
                        return CompletableFuture.completedFuture(null);
                    });

            when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
            when(documentRepository.findByIdAndUserId(documentId, userId.toString()))
                    .thenReturn(Optional.of(documentInformation));
            when(documentRepository.save(any(DocumentInformation.class))).thenReturn(documentInformation);

            // Use ArgumentCaptor to verify async history properties
            ArgumentCaptor<DocumentUserHistory> historyCaptor = ArgumentCaptor.forClass(DocumentUserHistory.class);
            when(documentUserHistoryRepository.save(historyCaptor.capture()))
                    .thenReturn(DocumentUserHistory.builder().build());

            // Use ArgumentCaptor to verify async event properties
            ArgumentCaptor<SyncEventRequest> eventCaptor = ArgumentCaptor.forClass(SyncEventRequest.class);
            doNothing().when(publishEventService).sendSyncEvent(eventCaptor.capture());

            // Act
            documentShareService.updateDocumentShareSettings(documentId, request, username);

            // Verify async operations were called with the right parameters
            verify(documentUserHistoryRepository).save(any(DocumentUserHistory.class));
            verify(publishEventService).sendSyncEvent(any(SyncEventRequest.class));
            verify(documentPreferencesService).recordInteraction(eq(userId), eq(documentId), any());

            // Verify history properties
            DocumentUserHistory history = historyCaptor.getValue();
            assertEquals(userId.toString(), history.getUserId());
            assertEquals(documentId, history.getDocumentId());
            assertEquals(UserDocumentActionType.SHARE, history.getUserDocumentActionType());
            assertEquals(1, history.getVersion());
            assertEquals("PUBLIC", history.getDetail());
            assertNotNull(history.getCreatedAt());

            // Verify sync event properties
            SyncEventRequest event = eventCaptor.getValue();
            assertEquals(userId.toString(), event.getUserId());
            assertEquals(documentId, event.getDocumentId());
            assertEquals(EventType.UPDATE_EVENT.name(), event.getSubject());
            assertNotNull(event.getTriggerAt());
        } finally {
            mockedCompletableFuture.close();
        }
    }

    @Test
    void updateDocumentShareSettings_ShouldValidateSharedUsers_WhenSpecificSharingRequested() {
        // Arrange
        Set<UUID> sharedWith = new HashSet<>(Arrays.asList(UUID.randomUUID(), UUID.randomUUID()));
        UpdateShareSettingsRequest request = new UpdateShareSettingsRequest(false, sharedWith);
        ResponseEntity<UserResponse> userResponseEntity = new ResponseEntity<>(userResponse, HttpStatus.OK);

        // Return only one user, simulating a non-existent user
        List<UserResponse> sharedUsers = List.of(
                new UserResponse(sharedWith.iterator().next(), "user1", "user1@example.com", null)
        );

        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);
        when(documentRepository.findByIdAndUserId(documentId, userId.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(userClient.getUsersByIds(anyList())).thenReturn(ResponseEntity.ok(sharedUsers));

        // Act & Assert
        assertThrows(InvalidDocumentException.class, () ->
                documentShareService.updateDocumentShareSettings(documentId, request, username));
    }

    @Test
    void searchShareableUsers_ShouldReturnUsersList_WhenSearchSucceeds() {
        // Arrange
        String query = "test";
        List<UserResponse> expectedUsers = Arrays.asList(
                new UserResponse(UUID.randomUUID(), "test1", "test1@example.com", null),
                new UserResponse(UUID.randomUUID(), "test2", "test2@example.com", null)
        );

        when(userClient.searchUsers(query)).thenReturn(ResponseEntity.ok(expectedUsers));

        // Act
        List<UserResponse> result = documentShareService.searchShareableUsers(query);

        // Assert
        assertNotNull(result);
        assertEquals(expectedUsers.size(), result.size());
        assertEquals(expectedUsers, result);
    }

    @Test
    void searchShareableUsers_ShouldReturnEmptyList_WhenExceptionOccurs() {
        // Arrange
        String query = "test";
        when(userClient.searchUsers(query)).thenThrow(new RuntimeException("API Error"));

        // Act
        List<UserResponse> result = documentShareService.searchShareableUsers(query);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getShareableUserDetails_ShouldReturnUserDetails_WhenUserIdsProvided() {
        // Arrange
        List<UUID> userIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        List<UserResponse> expectedUsers = Arrays.asList(
                new UserResponse(userIds.get(0), "user1", "user1@example.com", null),
                new UserResponse(userIds.get(1), "user2", "user2@example.com", null)
        );

        when(userClient.getUsersByIds(userIds)).thenReturn(ResponseEntity.ok(expectedUsers));

        // Act
        List<UserResponse> result = documentShareService.getShareableUserDetails(userIds);

        // Assert
        assertNotNull(result);
        assertEquals(expectedUsers.size(), result.size());
        assertEquals(expectedUsers, result);
    }

    @Test
    void getShareableUserDetails_ShouldReturnEmptyList_WhenExceptionOccurs() {
        // Arrange
        List<UUID> userIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        when(userClient.getUsersByIds(userIds)).thenThrow(new RuntimeException("API Error"));

        // Act
        List<UserResponse> result = documentShareService.getShareableUserDetails(userIds);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}