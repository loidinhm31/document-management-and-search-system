package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentRecommendation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.repository.DocumentRecommendationRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import com.dms.document.interaction.service.DocumentRecommendationService;
import com.dms.document.interaction.service.PublishEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentRecommendationServiceImpl implements DocumentRecommendationService {
    private final DocumentRecommendationRepository documentRecommendationRepository;
    private final DocumentRepository documentRepository;
    private final UserClient userClient;
    private final DocumentUserHistoryRepository documentUserHistoryRepository;
    private final PublishEventService publishEventService;


    @Override@Transactional
    public boolean recommendDocument(String documentId, boolean recommend, String username) {
        UserResponse userResponse = getUserInfo(username);

        // Verify document exists
        DocumentInformation document = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userResponse.userId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found or not accessible"));

        // Check if recommended
        Optional<DocumentRecommendation> documentRecommendation = documentRecommendationRepository.findByDocumentIdAndMentorId(
                documentId, userResponse.userId());

        if (recommend) {
            if (documentRecommendation.isPresent()) {
                return false; // Already recommended
            }

            // Create recommendation
            DocumentRecommendation recommendation = new DocumentRecommendation();
            recommendation.setDocumentId(documentId);
            recommendation.setMentorId(userResponse.userId());
            recommendation.setCreatedAt(Instant.now());
            documentRecommendationRepository.save(recommendation);

            // Update document recommendation count
            document.setRecommendationCount(Objects.nonNull(document.getRecommendationCount()) ? document.getRecommendationCount() + 1 : 0);

        } else {
            if (documentRecommendation.isEmpty()) {
                return false; // Not recommended
            }

            // Delete recommendation
            documentRecommendationRepository.delete(documentRecommendation.get());

            // Update document recommendation count
            document.setRecommendationCount(document.getRecommendationCount() - 1);
        }

        documentRepository.save(document);

        // Record history asynchronously
        CompletableFuture.runAsync(() -> {
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(com.dms.document.interaction.enums.UserDocumentActionType.RECOMMENDATION)
                    .version(document.getCurrentVersion())
                    .detail(recommend ? "ADD" : "REMOVE")
                    .createdAt(Instant.now())
                    .build());

            // Notify reindexing
            sendSyncEvent(document, userResponse.userId().toString());
        });

        return true;
    }

    @Override
    public boolean isDocumentRecommendedByUser(String documentId, String username) {
        UserResponse userResponse = getUserInfo(username);
        return documentRecommendationRepository.existsByDocumentIdAndMentorId(documentId, userResponse.userId());
    }

    private UserResponse getUserInfo(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        return response.getBody();
    }

    private void sendSyncEvent(DocumentInformation document, String userId) {
        publishEventService.sendSyncEvent(
                SyncEventRequest.builder()
                        .eventId(java.util.UUID.randomUUID().toString())
                        .userId(userId)
                        .documentId(document.getId())
                        .subject(EventType.UPDATE_EVENT.name())
                        .triggerAt(Instant.now())
                        .build()
        );
    }
}