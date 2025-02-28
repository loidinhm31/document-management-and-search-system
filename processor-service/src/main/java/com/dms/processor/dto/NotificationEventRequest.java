package com.dms.processor.dto;

import com.dms.processor.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationEventRequest extends EventRequest {
    private String documentId;
    private Long commentId;
    private String documentTitle;
    private NotificationType notificationType;
    private String triggerUserId;
    private Integer versionNumber;
}