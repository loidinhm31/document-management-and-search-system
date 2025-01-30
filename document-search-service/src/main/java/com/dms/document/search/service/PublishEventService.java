package com.dms.document.search.service;

import com.dms.document.search.dto.SyncEventRequest;
import com.dms.document.search.producer.RabbitMQMessageProducer;
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