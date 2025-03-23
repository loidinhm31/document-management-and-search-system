package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.NotificationEventRequest;
import com.dms.document.interaction.dto.SyncEventRequest;

public interface PublishEventService {
    void sendSyncEvent(SyncEventRequest syncEventRequest);
    void sendNotificationEvent(NotificationEventRequest notificationEvent);
}