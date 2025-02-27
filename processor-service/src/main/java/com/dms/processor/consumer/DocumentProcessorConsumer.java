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
public class DocumentProcessorConsumer {
    private final DocumentProcessService documentProcessService;
    private final DocumentRepository documentRepository;

    @RabbitListener(queues = "${rabbitmq.queues.document-process}")
    public void processDocumentEvent(SyncEventRequest request) {
        log.info("Processing document event: [ID: {}, Type: {}, Version: {}]",
                request.getEventId(),
                request.getSubject(),
                request.getVersionNumber());

        try {
            EventType eventType = EventType.valueOf(request.getSubject());
            switch (eventType) {
                case DELETE_EVENT -> handleDeleteEvent(request);
                case UPDATE_EVENT, UPDATE_EVENT_WITH_FILE -> handleUpdateEvent(request, eventType);
                case SYNC_EVENT -> handleSyncEvent(request, eventType);
                case REVERT_EVENT -> handleRevertEvent(request, eventType);
                case REPORT_PROCESS_EVENT -> handleReportStatus(request);
                default -> log.warn("Unhandled event type: {}", eventType);
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid event type: {}", request.getSubject());
        } catch (Exception e) {
            log.error("Error processing event: {}", request.getEventId(), e);
        }
    }

    private void handleRevertEvent(SyncEventRequest request, EventType eventType) {
        log.info("Processing revert event for document: {}", request.getDocumentId());
        findAndProcessDocument(request, eventType);
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

    private void handleReportStatus(SyncEventRequest request) {
        log.info("Processing report resolved event for document: {}", request.getDocumentId());
        documentProcessService.handleReportStatus(request.getDocumentId());
    }

    private void findAndProcessDocument(SyncEventRequest request, EventType eventType) {
        Optional<DocumentInformation> documentOpt = documentRepository.findById(request.getDocumentId());

        documentOpt.ifPresentOrElse(
                document -> {
                    log.info("Processing document: {}", document.getId());
                    documentProcessService.processDocument(document, request.getVersionNumber(), eventType);
                },
                () -> log.warn("Document not found: {}", request.getDocumentId())
        );
    }
}