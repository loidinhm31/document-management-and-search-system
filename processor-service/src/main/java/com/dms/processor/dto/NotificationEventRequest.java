package com.dms.processor.dto;

import com.dms.processor.enums.NotificationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
public class NotificationEventRequest extends EventRequest {
    private String documentId;
    private String documentTitle;
    private NotificationType notificationType;
    private String triggerUsername;
    private Integer versionNumber;
}