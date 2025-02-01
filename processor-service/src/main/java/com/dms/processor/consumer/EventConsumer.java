package com.dms.processor.consumer;

import com.dms.processor.dto.SyncEventRequest;
import com.dms.processor.enums.EventType;
import com.dms.processor.model.DocumentInformation;
import com.dms.processor.repository.DocumentRepository;
import com.dms.processor.service.DocumentProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventConsumer {
    private final DocumentProcessService documentProcessService;
    private final DocumentRepository documentRepository;

    @RabbitListener(queues = "${rabbitmq.queues.document-sync}")
    public void consumeSyncEvent(SyncEventRequest syncEventRequest) {
        log.info("Consumed event: [ID: {}, Type: {}, Version: {}]",
                syncEventRequest.getEventId(),
                syncEventRequest.getSubject(),
                syncEventRequest.getVersionNumber());

        try {
            EventType eventType = EventType.valueOf(syncEventRequest.getSubject());
            switch (eventType) {
                case DELETE_EVENT -> handleDeleteEvent(syncEventRequest);
                case UPDATE_EVENT, UPDATE_EVENT_WITH_FILE -> handleUpdateEvent(syncEventRequest, eventType);
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
        DocumentInformation document = documentRepository.findById(request.getDocumentId()).orElse(null);
        if (Objects.nonNull(document) &&
                StringUtils.isNotEmpty(document.getThumbnailPath())) {
            documentProcessService.deleteDocumentFromIndex(document.getId());
        }
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
                    log.info("Processing document: {}", document.getId());

                    // Index document
                    documentProcessService.processDocument(document, eventType);
                },
                () -> log.warn("Document not found: {}", request.getDocumentId())
        );
    }
}
