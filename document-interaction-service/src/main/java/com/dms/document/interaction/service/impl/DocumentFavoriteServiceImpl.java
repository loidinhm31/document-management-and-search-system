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
    public void favoriteDocument(String documentId, String username) {
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

        // Check for existing favorite
        if (documentFavoriteRepository.existsByUserIdAndDocumentId(userResponse.userId(), documentId)) {
            throw new DuplicateFavoriteException("Document already favorited");
        }

        DocumentFavorite favorite = new DocumentFavorite();
        favorite.setUserId(userResponse.userId());
        favorite.setDocumentId(documentId);
        documentFavoriteRepository.saveAndFlush(favorite);

        // Update document favorite count
        document.setFavoriteCount(document.getFavoriteCount() + 1);
        documentRepository.save(document);

        CompletableFuture.runAsync(() -> {
            // History
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.FAVORITE)
                    .version(document.getCurrentVersion())
                    .detail("ADD")
                    .createdAt(Instant.now())
                    .build());

            documentPreferencesService.recordInteraction(userResponse.userId(), documentId, InteractionType.FAVORITE);

            // Notify reindexing
            sendSyncEvent(document, userResponse.userId().toString());
        });
    }

    @Override
    public void unfavoriteDocument(String documentId, String username) {
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

        documentFavoriteRepository.deleteByUserIdAndDocumentId(userResponse.userId(), documentId);

        // Update document favorite count
        document.setFavoriteCount(document.getFavoriteCount() - 1);
        documentRepository.save(document);

        CompletableFuture.runAsync(() -> {
            // History
            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.FAVORITE)
                    .version(document.getCurrentVersion())
                    .detail("REMOVE")
                    .createdAt(Instant.now())
                    .build());

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