package com.dms.processor.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@NoArgsConstructor
@SuperBuilder
@Data
public class EventRequest {
    private String userId;

    private String eventId;

    private String subject;

    private Instant triggerAt;
}
