package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.NoteRequest;
import com.dms.document.interaction.dto.NoteResponse;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.DocumentStatus;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentNote;
import com.dms.document.interaction.model.UserDocumentHistory;
import com.dms.document.interaction.repository.DocumentNoteRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.UserDocumentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentNoteService {
    private final DocumentNoteRepository documentNoteRepository;
    private final DocumentRepository documentRepository;
    private final UserClient userClient;
    private final UserDocumentHistoryRepository userDocumentHistoryRepository;

    /**
     * Creates or updates a note by a mentor for a document
     *
     * @param documentId the document ID
     * @param request request containing the note content
     * @param username the username of the mentor
     * @return the created or updated note response
     */
    @Transactional
    public NoteResponse createOrUpdateNote(String documentId, NoteRequest request, String username) {
        // Get user info
        UserResponse mentor = getUserByUsername(username);

        // Check role
        if (!mentor.role().roleName().equals(AppRole.ROLE_MENTOR)) {
            throw new AccessDeniedException("Only mentors can add notes to documents");
        }

        // Validate content
        if (request.content() == null || request.content().trim().isEmpty()) {
            throw new IllegalArgumentException("Note content cannot be empty");
        }

        if (request.content().length() > 200) {
            throw new IllegalArgumentException("Note content exceeds maximum length of 200 characters");
        }

        // Check document exists and is accessible
        DocumentInformation document = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, mentor.userId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found or not accessible"));

        // Check document is successfully processed
        if (document.getStatus() != DocumentStatus.COMPLETED) {
            throw new InvalidDocumentException("Document must be successfully processed to add notes");
        }

        // Find existing note or create new
        Optional<DocumentNote> existingNote = documentNoteRepository.findByDocumentIdAndMentorId(
                documentId, mentor.userId());

        DocumentNote note;
        boolean isNew = false;

        if (existingNote.isPresent()) {
            // Update existing note
            note = existingNote.get();
            note.setContent(request.content());
            note.setUpdatedAt(Instant.now());
            note.setEdited(true);
        } else {
            // Create new note
            isNew = true;
            note = new DocumentNote();
            note.setDocumentId(documentId);
            note.setMentorId(mentor.userId());
            note.setContent(request.content());
            note.setCreatedAt(Instant.now());
            note.setUpdatedAt(Instant.now());
            note.setEdited(false);
        }

        DocumentNote savedNote = documentNoteRepository.save(note);

        // Record this action in history
        final boolean isNewNote = isNew;
        CompletableFuture.runAsync(() -> {
            userDocumentHistoryRepository.save(UserDocumentHistory.builder()
                    .userId(mentor.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.NOTE)
                    .version(document.getCurrentVersion())
                    .detail(isNewNote ? "ADD_NOTE" : "UPDATE_NOTE")
                    .createdAt(Instant.now())
                    .build());
        });

        return mapToNoteResponse(savedNote, mentor.username());
    }

    /**
     * Gets a note by the current mentor for a specific document
     *
     * @param documentId the document ID
     * @param username the username of the mentor
     * @return the note response if found
     */
    @Transactional(readOnly = true)
    public Optional<NoteResponse> getNoteByMentor(String documentId, String username) {
        UserResponse mentor = getUserByUsername(username);

        return documentNoteRepository.findByDocumentIdAndMentorId(documentId, mentor.userId())
                .map(note -> mapToNoteResponse(note, mentor.username()));
    }

    /**
     * Gets all notes for a document from all mentors
     *
     * @param documentId the document ID
     * @return list of all notes for the document
     */
    @Transactional(readOnly = true)
    public List<NoteResponse> getNotesByDocument(String documentId) {
        List<DocumentNote> notes = documentNoteRepository.findByDocumentId(documentId);

        if (notes.isEmpty()) {
            return Collections.emptyList();
        }

        // Get all mentor IDs
        Set<UUID> mentorIds = notes.stream()
                .map(DocumentNote::getMentorId)
                .collect(Collectors.toSet());

        // Batch fetch usernames
        Map<UUID, String> usernameMap = getUsernamesByIds(mentorIds);

        // Map notes to responses
        return notes.stream()
                .map(note -> mapToNoteResponse(note, usernameMap.getOrDefault(note.getMentorId(), "Unknown")))
                .collect(Collectors.toList());
    }

    /**
     * Checks if the current mentor has added a note to this document
     *
     * @param documentId the document ID
     * @param username the username of the mentor
     * @return true if the mentor has added a note, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasNoteForDocument(String documentId, String username) {
        UserResponse mentor = getUserByUsername(username);
        return documentNoteRepository.existsByDocumentIdAndMentorId(documentId, mentor.userId());
    }

    // Helper method to map note entity to response DTO
    private NoteResponse mapToNoteResponse(DocumentNote note, String mentorUsername) {
        return new NoteResponse(
                note.getId(),
                note.getDocumentId(),
                note.getMentorId(),
                mentorUsername,
                note.getContent(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                note.isEdited()
        );
    }

    // Helper to get user from username
    private UserResponse getUserByUsername(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        return response.getBody();
    }

    // Helper to batch fetch usernames
    private Map<UUID, String> getUsernamesByIds(Set<UUID> userIds) {
        try {
            ResponseEntity<List<UserResponse>> response = userClient.getUsersByIds(new ArrayList<>(userIds));
            List<UserResponse> users = response.getBody();
            if (users != null) {
                return users.stream()
                        .collect(Collectors.toMap(
                                UserResponse::userId,
                                UserResponse::username,
                                (u1, u2) -> u1 // In case of duplicates
                        ));
            }
        } catch (Exception e) {
            log.error("Error fetching usernames for note authors", e);
        }
        return new HashMap<>();
    }
}