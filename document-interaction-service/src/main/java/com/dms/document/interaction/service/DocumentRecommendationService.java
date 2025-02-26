package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentRecommendation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.repository.DocumentRecommendationRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
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
public class DocumentRecommendationService {
    private final DocumentRecommendationRepository recommendationRepository;
    private final DocumentRepository documentRepository;
    private final UserClient userClient;
    private final DocumentUserHistoryRepository documentUserHistoryRepository;
    private final PublishEventService publishEventService;

    @Transactional
    public boolean recommendDocument(String documentId, String username) {
        UserResponse userResponse = getUserInfo(username);

        // Verify user is a mentor
        if (!userResponse.role().roleName().equals(AppRole.ROLE_MENTOR)) {
            throw new InvalidDataAccessResourceUsageException("Only mentors can recommend documents");
        }

        // Verify document exists
        DocumentInformation document = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userResponse.userId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found or not accessible"));

        // Check if already recommended
        if (recommendationRepository.existsByDocumentIdAndMentorId(documentId, userResponse.userId())) {
            return false; // Already recommended
        }

        // Create recommendation
        DocumentRecommendation recommendation = new DocumentRecommendation();
        recommendation.setDocumentId(documentId);
        recommendation.setMentorId(userResponse.userId());
        recommendationRepository.save(recommendation);

        // Update document recommendation count
        long recommendationCount = recommendationRepository.countByDocumentId(documentId);
        document.setRecommendationCount((int) recommendationCount);
        documentRepository.save(document);

        // Record history asynchronously
        CompletableFuture.runAsync(() -> {
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(com.dms.document.interaction.enums.UserDocumentActionType.RECOMMENDATION)
                    .version(document.getCurrentVersion())
                    .detail("ADD_RECOMMENDATION")
                    .createdAt(Instant.now())
                    .build());

            // Notify OpenSearch for reindexing
            sendSyncEvent(document, userResponse.userId().toString());
        });

        return true;
    }

    @Transactional
    public boolean unrecommendDocument(String documentId, String username) {
        UserResponse userResponse = getUserInfo(username);

        // Verify user is a mentor
        if (!userResponse.role().roleName().equals(AppRole.ROLE_MENTOR)) {
            throw new InvalidDataAccessResourceUsageException("Only mentors can unrecommend documents");
        }

        // Verify document exists
        DocumentInformation document = documentRepository.findById(documentId)
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Check if recommended
        Optional<DocumentRecommendation> recommendation = recommendationRepository.findByDocumentIdAndMentorId(
                documentId, userResponse.userId());

        if (recommendation.isEmpty()) {
            return false; // Not recommended
        }

        // Delete recommendation
        recommendationRepository.delete(recommendation.get());

        // Update document recommendation count
        long recommendationCount = recommendationRepository.countByDocumentId(documentId);
        document.setRecommendationCount((int) recommendationCount);
        documentRepository.save(document);

        // Record history asynchronously
        CompletableFuture.runAsync(() -> {
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.RECOMMENDATION)
                    .version(document.getCurrentVersion())
                    .detail("REMOVE_RECOMMENDATION")
                    .createdAt(Instant.now())
                    .build());

            // Notify OpenSearch for reindexing
            sendSyncEvent(document, userResponse.userId().toString());
        });

        return true;
    }

    public boolean isDocumentRecommendedByUser(String documentId, String username) {
        UserResponse userResponse = getUserInfo(username);
        return recommendationRepository.existsByDocumentIdAndMentorId(documentId, userResponse.userId());
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