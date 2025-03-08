package com.dms.document.interaction.dto;

import com.dms.document.interaction.enums.NotificationType;
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
    private Long commentId;
    private String documentTitle;
    private NotificationType notificationType;
    private String triggerUserId;
    private Integer versionNumber;
}