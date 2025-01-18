package com.dms.document.service;

import com.dms.document.model.SyncEventRequest;
import com.dms.document.producer.RabbitMQMessageProducer;
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