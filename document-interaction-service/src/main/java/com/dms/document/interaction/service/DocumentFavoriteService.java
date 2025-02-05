package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.exception.DuplicateFavoriteException;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentFavorite;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.repository.DocumentFavoriteRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class DocumentFavoriteService {
    private final DocumentFavoriteRepository documentFavoriteRepository;
    private final DocumentRepository documentRepository;
    private final UserClient userClient;

    public void favoriteDocument(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        // Check if document exists
        documentRepository.findById(documentId)
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Check for existing favorite
        if (documentFavoriteRepository.existsByUserIdAndDocumentId(userResponse.userId(), documentId)) {
            throw new DuplicateFavoriteException("Document already favorited");
        }

        DocumentFavorite favorite = new DocumentFavorite();
        favorite.setUserId(userResponse.userId());
        favorite.setDocumentId(documentId);
        documentFavoriteRepository.save(favorite);
    }

    public void unfavoriteDocument(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();
        documentFavoriteRepository.deleteByUserIdAndDocumentId(userResponse.userId(), documentId);
    }

    public boolean isDocumentFavorited(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        return documentFavoriteRepository.existsByUserIdAndDocumentId(userResponse.userId(), documentId);
    }

    public Page<DocumentInformation> getFavoritedDocuments(Pageable pageable, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        // Get favorites from database
        Page<DocumentFavorite> favorites = documentFavoriteRepository.findByUserId(userResponse.userId(), pageable);

        // Get document IDs
        List<String> documentIds = favorites.getContent().stream()
                .map(DocumentFavorite::getDocumentId)
                .collect(Collectors.toList());

        // Fetch documents
        List<DocumentInformation> documents = documentRepository.findByIdIn(documentIds);

        // Maintain order from favorites
        Map<String, DocumentInformation> documentMap = documents.stream()
                .collect(Collectors.toMap(DocumentInformation::getId, d -> d));

        List<DocumentInformation> orderedDocuments = documentIds.stream()
                .map(documentMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PageImpl<>(orderedDocuments, pageable, favorites.getTotalElements());
    }
}