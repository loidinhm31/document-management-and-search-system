package com.dms.processor.consumer;

import com.dms.processor.enums.EventType;
import com.dms.processor.model.DocumentInformation;
import com.dms.processor.dto.SyncEventRequest;
import com.dms.processor.repository.DocumentRepository;
import com.dms.processor.service.DocumentProcessService;
import com.dms.processor.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
                        log.info("Processing document: {}", document.getId());

                        // Index document
                        documentProcessService.indexDocument(document, eventType);

                        // Generate thumbnail if needed (base on extracted content)
                        if (eventType == EventType.SYNC_EVENT || eventType == EventType.UPDATE_EVENT_WITH_FILE) {
                            try {
                                String thumbnailPath = documentProcessService.generateAndSaveThumbnail(document);
                                document.setThumbnailPath(thumbnailPath);
                                documentRepository.save(document);
                            } catch (Exception e) {
                                log.error("Error generating thumbnail for document: {}", document.getId(), e);
                            }
                        }
                    }
                },
                () -> log.warn("Document not found: {}", request.getDocumentId())
        );
    }
}
