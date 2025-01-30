package com.dms.document.search.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@NoArgsConstructor
@SuperBuilder
@Data
public class EventRequest {
    private String userId;

    private String eventId;

    private String subject;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'ZZZZZ'")
    private LocalDateTime triggerAt;
}
