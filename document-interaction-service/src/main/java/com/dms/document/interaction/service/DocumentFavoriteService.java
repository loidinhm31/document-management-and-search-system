package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.InteractionType;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.exception.DuplicateFavoriteException;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentFavorite;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.UserDocumentHistory;
import com.dms.document.interaction.repository.DocumentFavoriteRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.UserDocumentHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class DocumentFavoriteService {
    private final DocumentFavoriteRepository documentFavoriteRepository;
    private final DocumentRepository documentRepository;
    private final UserClient userClient;
    private final DocumentPreferencesService documentPreferencesService;
    private final UserDocumentHistoryRepository userDocumentHistoryRepository;

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
        DocumentInformation doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Check for existing favorite
        if (documentFavoriteRepository.existsByUserIdAndDocumentId(userResponse.userId(), documentId)) {
            throw new DuplicateFavoriteException("Document already favorited");
        }

        DocumentFavorite favorite = new DocumentFavorite();
        favorite.setUserId(userResponse.userId());
        favorite.setDocumentId(documentId);
        documentFavoriteRepository.save(favorite);

        CompletableFuture.runAsync(() -> {
            // History
            userDocumentHistoryRepository.save(UserDocumentHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.FAVORITE)
                    .version(doc.getCurrentVersion())
                    .detail("ADD")
                    .createdAt(Instant.now())
                    .build());

            documentPreferencesService.recordInteraction(userResponse.userId(), documentId, InteractionType.FAVORITE);
        });
    }

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
        DocumentInformation doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        documentFavoriteRepository.deleteByUserIdAndDocumentId(userResponse.userId(), documentId);

        CompletableFuture.runAsync(() -> {
            // History
            userDocumentHistoryRepository.save(UserDocumentHistory.builder()
                    .userId(userResponse.userId().toString())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.FAVORITE)
                    .version(doc.getCurrentVersion())
                    .detail("REMOVE")
                    .createdAt(Instant.now())
                    .build());
        });
    }

    public boolean isDocumentFavorited(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();
        if (!(Objects.equals(userResponse.role().roleName(), AppRole.ROLE_USER) ||
              Objects.equals(userResponse.role().roleName(), AppRole.ROLE_MENTOR))) {
            throw new InvalidDataAccessResourceUsageException("Invalid role");
        }
        return documentFavoriteRepository.existsByUserIdAndDocumentId(userResponse.userId(), documentId);
    }
}