package com.sdms.document.service;

import com.sdms.document.model.SyncEventRequest;
import com.sdms.document.producer.RabbitMQMessageProducer;
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
}