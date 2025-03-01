package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.NotificationEventRequest;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.producer.RabbitMQMessageProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PublishEventService {
    @Value("${rabbitmq.exchanges.document}")
    private String documentExchange;

    @Value("${rabbitmq.exchanges.notification}")
    private String notificationExchange;

    @Value("${rabbitmq.routing-keys.document-process}")
    private String documentProcessRoutingKey;


    private final RabbitMQMessageProducer rabbitMQMessageProducer;

    public void sendSyncEvent(SyncEventRequest syncEventRequest) {
        rabbitMQMessageProducer.publish(
                syncEventRequest,
                documentExchange,
                documentProcessRoutingKey
        );
    }

    public void sendNotificationEvent(NotificationEventRequest notificationEvent) {
        rabbitMQMessageProducer.publish(
                notificationEvent,
                notificationExchange,
                documentProcessRoutingKey
        );
    }
}