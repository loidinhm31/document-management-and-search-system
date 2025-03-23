package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.dto.NotificationEventRequest;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.producer.RabbitMQMessageProducer;
import com.dms.document.interaction.service.PublishEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PublishEventServiceImpl implements PublishEventService {
    @Value("${rabbitmq.exchanges.document}")
    private String documentExchange;

    @Value("${rabbitmq.exchanges.notification}")
    private String notificationExchange;

    @Value("${rabbitmq.routing-keys.document-process}")
    private String documentProcessRoutingKey;


    private final RabbitMQMessageProducer rabbitMQMessageProducer;

    @Override
    public void sendSyncEvent(SyncEventRequest syncEventRequest) {
        rabbitMQMessageProducer.publish(
                syncEventRequest,
                documentExchange,
                documentProcessRoutingKey
        );
    }

    @Override
    public void sendNotificationEvent(NotificationEventRequest notificationEvent) {
        rabbitMQMessageProducer.publish(
                notificationEvent,
                notificationExchange,
                documentProcessRoutingKey
        );
    }
}