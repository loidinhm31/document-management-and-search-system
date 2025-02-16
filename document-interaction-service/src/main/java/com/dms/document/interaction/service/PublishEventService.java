package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.NotificationEventRequest;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.producer.RabbitMQMessageProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PublishEventService {
    private final RabbitMQMessageProducer rabbitMQMessageProducer;

    public void sendSyncEvent(SyncEventRequest syncEventRequest) {
        rabbitMQMessageProducer.publish(
                syncEventRequest,
                "internal.exchange",
                "internal.document-sync.routing-key"
        );
    }

    public void sendNotificationEvent(NotificationEventRequest notificationEvent) {
        rabbitMQMessageProducer.publish(
                notificationEvent,
                "notification.exchange",
                "notification.email.routing-key"
        );
    }
}