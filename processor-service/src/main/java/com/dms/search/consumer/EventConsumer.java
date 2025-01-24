package com.dms.search.consumer;

import com.dms.search.enums.EventType;
import com.dms.search.model.DocumentInformation;
import com.dms.search.model.SyncEventRequest;
import com.dms.search.repository.DocumentRepository;
import com.dms.search.service.DocumentProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventConsumer {
    private final DocumentProcessService documentProcessService;
    private final DocumentRepository documentRepository;

    @RabbitListener(queues = "${rabbitmq.queues.document-sync}")
    public void consumeSyncEvent(SyncEventRequest syncEventRequest) {
        log.info("Consumed event: [ID: {}, Type: {}]",
                syncEventRequest.getEventId(),
                syncEventRequest.getSubject());

        try {
            EventType eventType = EventType.valueOf(syncEventRequest.getSubject());
            switch (eventType) {
                case DELETE_EVENT -> handleDeleteEvent(syncEventRequest);
                case UPDATE_EVENT -> handleUpdateEvent(syncEventRequest, eventType);
                case UPDATE_EVENT_WITH_FILE -> handleUpdateEvent(syncEventRequest, eventType);
                case SYNC_EVENT -> handleSyncEvent(syncEventRequest, eventType);
                default -> log.warn("Unhandled event type: {}", eventType);
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid event type: {}", syncEventRequest.getSubject());
        } catch (Exception e) {
            log.error("Error processing event: {}", syncEventRequest.getEventId(), e);
        }
    }

    private void handleDeleteEvent(SyncEventRequest request) {
        log.info("Processing delete event for document: {}", request.getDocumentId());
        documentProcessService.deleteDocumentFromIndex(request.getDocumentId());
    }

    private void handleUpdateEvent(SyncEventRequest request, EventType eventType) {
        log.info("Processing update event for document: {}", request.getDocumentId());
        findAndProcessDocument(request, eventType);
    }

    private void handleSyncEvent(SyncEventRequest request, EventType eventType) {
        log.info("Processing sync event for document: {}", request.getDocumentId());
        findAndProcessDocument(request, eventType);
    }

    private void findAndProcessDocument(SyncEventRequest request, EventType eventType) {
        Optional<DocumentInformation> documentOpt = documentRepository.findByIdAndUserId(
                request.getDocumentId(),
                request.getUserId()
        );

        documentOpt.ifPresentOrElse(
                document -> {
                    if (document.isDeleted()) {
                        log.info("Document {} is marked as deleted, removing from index", document.getId());
                        documentProcessService.deleteDocumentFromIndex(document.getId());
                    } else {
                        log.info("Indexing document: {}", document.getId());
                        documentProcessService.indexDocument(document, eventType);
                    }
                },
                () -> log.warn("Document not found: {}", request.getDocumentId())
        );
    }
}
