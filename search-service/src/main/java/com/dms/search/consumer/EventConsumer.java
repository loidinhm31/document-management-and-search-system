package com.dms.search.consumer;

import com.dms.search.enums.EventType;
import com.dms.search.model.DocumentInformation;
import com.dms.search.model.SyncEventRequest;
import com.dms.search.repository.DocumentRepository;
import com.dms.search.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class EventConsumer {
    private final DocumentService documentService;
    private final DocumentRepository documentRepository;

    public EventConsumer(DocumentService documentService, DocumentRepository documentRepository) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
    }

    @RabbitListener(queues = "${rabbitmq.queues.document-sync}")
    public void consumeSyncEvent(SyncEventRequest syncEventRequest) {
        log.info("Consumed event: [ID: {}, Type: {}]",
                syncEventRequest.getEventId(),
                syncEventRequest.getSubject());

        try {
            EventType eventType = EventType.valueOf(syncEventRequest.getSubject());
            switch (eventType) {
                case DELETE_EVENT -> handleDeleteEvent(syncEventRequest);
                case UPDATE_EVENT -> handleUpdateEvent(syncEventRequest);
                case SYNC_EVENT -> handleSyncEvent(syncEventRequest);
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
        documentService.deleteDocumentFromIndex(request.getDocumentId());
    }

    private void handleUpdateEvent(SyncEventRequest request) {
        log.info("Processing update event for document: {}", request.getDocumentId());
        findAndProcessDocument(request);
    }

    private void handleSyncEvent(SyncEventRequest request) {
        log.info("Processing sync event for document: {}", request.getDocumentId());
        findAndProcessDocument(request);
    }

    private void findAndProcessDocument(SyncEventRequest request) {
        Optional<DocumentInformation> documentOpt = documentRepository.findByIdAndUserId(
                request.getDocumentId(),
                request.getUserId()
        );

        documentOpt.ifPresentOrElse(
                document -> {
                    if (document.isDeleted()) {
                        log.info("Document {} is marked as deleted, removing from index", document.getId());
                        documentService.deleteDocumentFromIndex(document.getId());
                    } else {
                        log.info("Indexing document: {}", document.getId());
                        documentService.indexDocument(document);
                    }
                },
                () -> log.warn("Document not found: {}", request.getDocumentId())
        );
    }
}
