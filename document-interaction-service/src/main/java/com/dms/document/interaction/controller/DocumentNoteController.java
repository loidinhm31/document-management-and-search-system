package com.dms.document.interaction.controller;

import com.dms.document.interaction.constant.ApiConstant;
import com.dms.document.interaction.dto.NoteRequest;
import com.dms.document.interaction.dto.NoteResponse;
import com.dms.document.interaction.service.DocumentNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiConstant.API_VERSION + ApiConstant.DOCUMENT_BASE_PATH + ApiConstant.DOCUMENT_ID_PATH + "/notes")
@RequiredArgsConstructor
@Tag(name = "Document Notes", description = "APIs for managing document notes by mentors")
public class DocumentNoteController {
    private final DocumentNoteService documentNoteService;

    @Operation(summary = "Add or update note for document",
            description = "Allows mentors to add or update a note for a document")
    @PostMapping
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<NoteResponse> createOrUpdateNote(
            @PathVariable String id,
            @RequestBody NoteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentNoteService.createOrUpdateNote(id, request, jwt.getSubject()));
    }

    @Operation(summary = "Get mentor's note for document",
            description = "Get the current mentor's note for a specific document if exists")
    @GetMapping("/creator")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<NoteResponse> getMentorNote(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return documentNoteService.getNoteByMentor(id, jwt.getSubject())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Check if mentor has added a note",
            description = "Check if the current mentor has already added a note to this document")
    @GetMapping("/status")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<Boolean> hasNote(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(documentNoteService.hasNoteForDocument(id, jwt.getSubject()));
    }

    @Operation(summary = "Get all notes for document",
            description = "Get all mentor notes for a specific document")
    @GetMapping
    public ResponseEntity<List<NoteResponse>> getAllNotes(
            @PathVariable String id) {
        return ResponseEntity.ok(documentNoteService.getNotesByDocument(id));
    }
}