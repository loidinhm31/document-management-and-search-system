package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.NoteRequest;
import com.dms.document.interaction.dto.NoteResponse;

import java.util.List;

/**
 * Service interface for managing document notes.
 */
public interface DocumentNoteService {

    /**
     * Creates or updates a note for a document.
     *
     * @param documentId The document ID
     * @param request The note request containing the content
     * @param username The username of the mentor creating/updating the note
     * @return NoteResponse containing the created/updated note details
     */
    NoteResponse createOrUpdateNote(String documentId, NoteRequest request, String username);

    /**
     * Gets all notes for a document.
     *
     * @param documentId The document ID
     * @return List of note responses
     */
    List<NoteResponse> getNotesByDocument(String documentId);

    /**
     * Checks if a user has a note for a document.
     *
     * @param documentId The document ID
     * @param username The username of the user
     * @return True if the user has a note for the document, false otherwise
     */
    boolean hasNoteForDocument(String documentId, String username);
}