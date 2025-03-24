package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.NoteRequest;
import com.dms.document.interaction.dto.NoteResponse;
import com.dms.document.interaction.dto.RoleResponse;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.DocumentStatus;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentNote;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.repository.DocumentNoteRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentNoteServiceImplTest {

    @Mock
    private DocumentNoteRepository documentNoteRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private DocumentUserHistoryRepository documentUserHistoryRepository;

    @InjectMocks
    private DocumentNoteServiceImpl documentNoteService;

    @Captor
    private ArgumentCaptor<DocumentNote> documentNoteCaptor;

    @Captor
    private ArgumentCaptor<DocumentUserHistory> historyCaptor;

    private final String documentId = "doc123";
    private final String username = "mentor1";
    private final UUID mentorId = UUID.randomUUID();
    private final String noteContent = "This is a test note";

    private UserResponse mockUserResponse;
    private DocumentInformation mockDocument;
    private DocumentNote mockDocumentNote;

    @BeforeEach
    public void setup() {
        // Setup mock user response
        RoleResponse roleResponse = new RoleResponse(UUID.randomUUID(), null);
        mockUserResponse = new UserResponse(mentorId, username, "mentor@example.com", roleResponse);

        // Setup mock document
        mockDocument = DocumentInformation.builder()
                .id(documentId)
                .status(DocumentStatus.COMPLETED)
                .currentVersion(1)
                .build();

        // Setup mock document note
        mockDocumentNote = new DocumentNote();
        mockDocumentNote.setId(1L);
        mockDocumentNote.setDocumentId(documentId);
        mockDocumentNote.setMentorId(mentorId);
        mockDocumentNote.setContent(noteContent);
        mockDocumentNote.setCreatedAt(Instant.now());
        mockDocumentNote.setUpdatedAt(Instant.now());
        mockDocumentNote.setEdited(false);
    }

    @Test
    public void testCreateNote_Success() {
        // Arrange
        NoteRequest request = new NoteRequest(noteContent);
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(mockUserResponse, HttpStatus.OK));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(documentId, mentorId.toString()))
                .thenReturn(Optional.of(mockDocument));
        when(documentNoteRepository.findByDocumentIdAndMentorId(documentId, mentorId))
                .thenReturn(Optional.empty());
        when(documentNoteRepository.save(any(DocumentNote.class))).thenAnswer(i -> i.getArgument(0));

        // Note: We don't set up mock for documentUserHistoryRepository.save()
        // because it's called asynchronously in CompletableFuture.runAsync()
        // and may not execute during the test

        // Act
        NoteResponse response = documentNoteService.createOrUpdateNote(documentId, request, username);

        // Assert
        assertNotNull(response);
        assertEquals(documentId, response.documentId());
        assertEquals(mentorId, response.mentorId());
        assertEquals(username, response.mentorUsername());
        assertEquals(noteContent, response.content());
        assertFalse(response.edited());

        // Verify the note was saved
        verify(documentNoteRepository).save(documentNoteCaptor.capture());
        DocumentNote capturedNote = documentNoteCaptor.getValue();
        assertEquals(documentId, capturedNote.getDocumentId());
        assertEquals(mentorId, capturedNote.getMentorId());
        assertEquals(noteContent, capturedNote.getContent());
        assertFalse(capturedNote.isEdited());
        assertNotNull(capturedNote.getCreatedAt());
        assertNotNull(capturedNote.getUpdatedAt());

        // We cannot verify async operations in a unit test
        // In a real application, you might use an integration test to verify this
    }

    @Test
    public void testUpdateNote_Success() {
        // Arrange
        String updatedContent = "Updated note content";
        NoteRequest request = new NoteRequest(updatedContent);
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(mockUserResponse, HttpStatus.OK));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(documentId, mentorId.toString()))
                .thenReturn(Optional.of(mockDocument));
        when(documentNoteRepository.findByDocumentIdAndMentorId(documentId, mentorId))
                .thenReturn(Optional.of(mockDocumentNote));
        when(documentNoteRepository.save(any(DocumentNote.class))).thenAnswer(i -> i.getArgument(0));

        // Note: We don't set up mock for documentUserHistoryRepository.save()
        // because it's called asynchronously in CompletableFuture.runAsync()

        // Act
        NoteResponse response = documentNoteService.createOrUpdateNote(documentId, request, username);

        // Assert
        assertNotNull(response);
        assertEquals(documentId, response.documentId());
        assertEquals(mentorId, response.mentorId());
        assertEquals(username, response.mentorUsername());
        assertEquals(updatedContent, response.content());
        assertTrue(response.edited());

        // Verify the note was updated
        verify(documentNoteRepository).save(documentNoteCaptor.capture());
        DocumentNote capturedNote = documentNoteCaptor.getValue();
        assertEquals(documentId, capturedNote.getDocumentId());
        assertEquals(mentorId, capturedNote.getMentorId());
        assertEquals(updatedContent, capturedNote.getContent());
        assertTrue(capturedNote.isEdited());
        assertNotNull(capturedNote.getUpdatedAt());

        // Cannot verify async operations directly in unit tests
    }

    @Test
    public void testCreateNote_EmptyContent() {
        // Arrange
        NoteRequest request = new NoteRequest("");
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(mockUserResponse, HttpStatus.OK));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                documentNoteService.createOrUpdateNote(documentId, request, username)
        );
        assertEquals("Note content cannot be empty", exception.getMessage());

        // Verify no interactions with repositories
        verify(documentNoteRepository, never()).save(any());
        verify(documentUserHistoryRepository, never()).save(any());
    }

    @Test
    public void testCreateNote_ContentTooLong() {
        // Arrange
        String longContent = "a".repeat(201); // More than 200 characters
        NoteRequest request = new NoteRequest(longContent);
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(mockUserResponse, HttpStatus.OK));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                documentNoteService.createOrUpdateNote(documentId, request, username)
        );
        assertEquals("Note content exceeds maximum length of 200 characters", exception.getMessage());

        // Verify no interactions with repositories
        verify(documentNoteRepository, never()).save(any());
        verify(documentUserHistoryRepository, never()).save(any());
    }

    @Test
    public void testCreateNote_DocumentNotFound() {
        // Arrange
        NoteRequest request = new NoteRequest(noteContent);
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(mockUserResponse, HttpStatus.OK));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(documentId, mentorId.toString()))
                .thenReturn(Optional.empty());

        // Act & Assert
        InvalidDocumentException exception = assertThrows(InvalidDocumentException.class, () ->
                documentNoteService.createOrUpdateNote(documentId, request, username)
        );
        assertEquals("Document not found or not accessible", exception.getMessage());

        // Verify no interactions with repositories
        verify(documentNoteRepository, never()).save(any());
        verify(documentUserHistoryRepository, never()).save(any());
    }

    @Test
    public void testCreateNote_DocumentNotProcessed() {
        // Arrange
        NoteRequest request = new NoteRequest(noteContent);
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(mockUserResponse, HttpStatus.OK));

        // Set document status to PENDING
        DocumentInformation pendingDocument = DocumentInformation.builder()
                .id(documentId)
                .status(DocumentStatus.PENDING)
                .build();

        when(documentRepository.findAccessibleDocumentByIdAndUserId(documentId, mentorId.toString()))
                .thenReturn(Optional.of(pendingDocument));

        // Act & Assert
        InvalidDocumentException exception = assertThrows(InvalidDocumentException.class, () ->
                documentNoteService.createOrUpdateNote(documentId, request, username)
        );
        assertEquals("Document must be successfully processed to add notes", exception.getMessage());

        // Verify no interactions with repositories
        verify(documentNoteRepository, never()).save(any());
        verify(documentUserHistoryRepository, never()).save(any());
    }

    @Test
    public void testCreateNote_UserNotFound() {
        // Arrange
        NoteRequest request = new NoteRequest(noteContent);
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(null, HttpStatus.NOT_FOUND));

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentNoteService.createOrUpdateNote(documentId, request, username)
        );

        // Verify no interactions with repositories
        verify(documentRepository, never()).findAccessibleDocumentByIdAndUserId(anyString(), anyString());
        verify(documentNoteRepository, never()).save(any());
        verify(documentUserHistoryRepository, never()).save(any());
    }

    @Test
    public void testGetNotesByDocument_Success() {
        // Arrange
        List<DocumentNote> notes = List.of(mockDocumentNote);
        when(documentNoteRepository.findByDocumentId(documentId)).thenReturn(notes);

        List<UserResponse> userResponses = List.of(mockUserResponse);
        when(userClient.getUsersByIds(anyList())).thenReturn(new ResponseEntity<>(userResponses, HttpStatus.OK));

        // Act
        List<NoteResponse> response = documentNoteService.getNotesByDocument(documentId);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.size());
        NoteResponse noteResponse = response.get(0);
        assertEquals(documentId, noteResponse.documentId());
        assertEquals(mentorId, noteResponse.mentorId());
        assertEquals(username, noteResponse.mentorUsername());
        assertEquals(noteContent, noteResponse.content());
    }

    @Test
    public void testGetNotesByDocument_Empty() {
        // Arrange
        when(documentNoteRepository.findByDocumentId(documentId)).thenReturn(Collections.emptyList());

        // Act
        List<NoteResponse> response = documentNoteService.getNotesByDocument(documentId);

        // Assert
        assertNotNull(response);
        assertTrue(response.isEmpty());
        verify(userClient, never()).getUsersByIds(anyList());
    }

    @Test
    public void testGetNotesByDocument_UserClientError() {
        // Arrange
        List<DocumentNote> notes = List.of(mockDocumentNote);
        when(documentNoteRepository.findByDocumentId(documentId)).thenReturn(notes);
        when(userClient.getUsersByIds(anyList())).thenThrow(new RuntimeException("API Error"));

        // Act
        List<NoteResponse> response = documentNoteService.getNotesByDocument(documentId);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.size());
        NoteResponse noteResponse = response.get(0);
        assertEquals(documentId, noteResponse.documentId());
        assertEquals(mentorId, noteResponse.mentorId());
        assertEquals("Unknown", noteResponse.mentorUsername()); // Default username when API fails
        assertEquals(noteContent, noteResponse.content());
    }

    @Test
    public void testHasNoteForDocument_True() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(mockUserResponse, HttpStatus.OK));
        when(documentNoteRepository.existsByDocumentIdAndMentorId(documentId, mentorId)).thenReturn(true);

        // Act
        boolean result = documentNoteService.hasNoteForDocument(documentId, username);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testHasNoteForDocument_False() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(mockUserResponse, HttpStatus.OK));
        when(documentNoteRepository.existsByDocumentIdAndMentorId(documentId, mentorId)).thenReturn(false);

        // Act
        boolean result = documentNoteService.hasNoteForDocument(documentId, username);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testHasNoteForDocument_UserNotFound() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(new ResponseEntity<>(null, HttpStatus.NOT_FOUND));

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentNoteService.hasNoteForDocument(documentId, username)
        );

        // Verify no interactions with note repository
        verify(documentNoteRepository, never()).existsByDocumentIdAndMentorId(anyString(), any(UUID.class));
    }
}