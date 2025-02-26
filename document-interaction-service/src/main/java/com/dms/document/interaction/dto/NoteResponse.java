package com.dms.document.interaction.dto;

import java.time.Instant;
import java.util.UUID;

public record NoteResponse(
        Long id,
        String documentId,
        UUID mentorId,
        String mentorUsername,
        String content,
        Instant createdAt,
        Instant updatedAt,
        boolean edited
) {}