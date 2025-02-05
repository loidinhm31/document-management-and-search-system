package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.exception.DuplicateBookmarkException;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentBookmark;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.repository.DocumentBookmarkRepository;
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
public class DocumentBookmarkService {
    private final DocumentBookmarkRepository bookmarkRepository;
    private final DocumentRepository documentRepository;
    private final UserClient userClient;

    public void bookmarkDocument(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        // Check if document exists
        documentRepository.findById(documentId)
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Check for existing bookmark
        if (bookmarkRepository.existsByUserIdAndDocumentId(userResponse.userId(), documentId)) {
            throw new DuplicateBookmarkException("Document already bookmarked");
        }

        DocumentBookmark bookmark = new DocumentBookmark();
        bookmark.setUserId(userResponse.userId());
        bookmark.setDocumentId(documentId);
        bookmarkRepository.save(bookmark);
    }

    public void unbookmarkDocument(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();
        bookmarkRepository.deleteByUserIdAndDocumentId(userResponse.userId(), documentId);
    }

    public boolean isDocumentBookmarked(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        return bookmarkRepository.existsByUserIdAndDocumentId(userResponse.userId(), documentId);
    }

    public Page<DocumentInformation> getBookmarkedDocuments(Pageable pageable, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        // Get bookmarks from PostgreSQL
        Page<DocumentBookmark> bookmarks = bookmarkRepository.findByUserId(userResponse.userId(), pageable);

        // Get document IDs
        List<String> documentIds = bookmarks.getContent().stream()
                .map(DocumentBookmark::getDocumentId)
                .collect(Collectors.toList());

        // Fetch documents
        List<DocumentInformation> documents = documentRepository.findByIdIn(documentIds);

        // Maintain order from bookmarks
        Map<String, DocumentInformation> documentMap = documents.stream()
                .collect(Collectors.toMap(DocumentInformation::getId, d -> d));

        List<DocumentInformation> orderedDocuments = documentIds.stream()
                .map(documentMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PageImpl<>(orderedDocuments, pageable, bookmarks.getTotalElements());
    }
}