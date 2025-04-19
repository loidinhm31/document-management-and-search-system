package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.DocumentFavoriteCheck;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.InteractionType;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.exception.DuplicateFavoriteException;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentFavorite;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentRecommendation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.repository.DocumentFavoriteRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import com.dms.document.interaction.service.DocumentFavoriteService;
import com.dms.document.interaction.service.DocumentPreferencesService;
import com.dms.document.interaction.service.PublishEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
@RequiredArgsConstructor
public class DocumentFavoriteServiceImpl implements DocumentFavoriteService {
    private final DocumentFavoriteRepository documentFavoriteRepository;
    private final DocumentRepository documentRepository;
    private final UserClient userClient;
    private final DocumentPreferencesService documentPreferencesService;
    private final DocumentUserHistoryRepository documentUserHistoryRepository;
    private final PublishEventService publishEventService;

    @Override
    public void favoriteDocument(String documentId, boolean favorite, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();
        if (!(Objects.equals(userResponse.role().roleName(), AppRole.ROLE_USER) ||
              Objects.equals(userResponse.role().roleName(), AppRole.ROLE_MENTOR))) {
            throw new InvalidDataAccessResourceUsageException("Invalid role");
        }

        // Check if document exists
        DocumentInformation document = documentRepository.findById(documentId)
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Check if recommended
        boolean isFavorited = documentFavoriteRepository.existsByUserIdAndDocumentId(userResponse.userId(), documentId);

        // Update document favorite count
        if (favorite) {
            // Check for existing favorite
            if (isFavorited) {
                throw new DuplicateFavoriteException("Document already favorited");
            }

            DocumentFavorite documentfavorite = new DocumentFavorite();
            documentfavorite.setUserId(userResponse.userId());
            documentfavorite.setDocumentId(documentId);
            documentFavoriteRepository.save(documentfavorite);

            document.setFavoriteCount(Objects.nonNull(document.getFavoriteCount()) ? document.getFavoriteCount() + 1 : 0);
        } else {
            // Check for existing favorite
            if (!isFavorited) {
                throw new DuplicateFavoriteException("Document not favorited");
            }

            documentFavoriteRepository.deleteByUserIdAndDocumentId(userResponse.userId(), documentId);

            document.setFavoriteCount(document.getFavoriteCount() - 1);
        }
        documentRepository.save(document);

        CompletableFuture.runAsync(() -> {
            // History
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.FAVORITE)
                    .version(document.getCurrentVersion())
                    .detail(favorite ? "ADD" : "REMOVE")
                    .createdAt(Instant.now())
                    .build());

            documentPreferencesService.recordInteraction(userResponse.userId(), documentId, InteractionType.FAVORITE);

            // Notify reindexing
            sendSyncEvent(document, userResponse.userId().toString());
        });
    }

    @Override
    public DocumentFavoriteCheck checkDocumentFavorited(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();
        if (!(Objects.equals(userResponse.role().roleName(), AppRole.ROLE_USER) ||
              Objects.equals(userResponse.role().roleName(), AppRole.ROLE_MENTOR))) {
            throw new InvalidDataAccessResourceUsageException("Invalid role");
        }
        boolean isDocumentFavorited = documentFavoriteRepository.existsByUserIdAndDocumentId(userResponse.userId(), documentId);
        long favoriteCount = documentFavoriteRepository.countByDocumentId(documentId);

        return new DocumentFavoriteCheck(isDocumentFavorited, (int) favoriteCount);
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