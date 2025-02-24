package com.dms.document.interaction.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDateTime;

@NoArgsConstructor
@SuperBuilder
@Data
public class EventRequest {
    private String userId;

    private String eventId;

    private String subject;

    private Instant triggerAt;
}
