package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.UserDto;
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
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        // Check if document exists
        documentRepository.findById(documentId)
                .orElseThrow(() -> new InvalidDocumentException("Document not found"));

        // Check for existing bookmark
        if (bookmarkRepository.existsByUserIdAndDocumentId(userDto.getUserId(), documentId)) {
            throw new DuplicateBookmarkException("Document already bookmarked");
        }

        DocumentBookmark bookmark = new DocumentBookmark();
        bookmark.setUserId(userDto.getUserId());
        bookmark.setDocumentId(documentId);
        bookmarkRepository.save(bookmark);
    }

    public void unbookmarkDocument(String documentId, String username) {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();
        bookmarkRepository.deleteByUserIdAndDocumentId(userDto.getUserId(), documentId);
    }

    public boolean isDocumentBookmarked(String documentId, String username) {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        return bookmarkRepository.existsByUserIdAndDocumentId(userDto.getUserId(), documentId);
    }

    public Page<DocumentInformation> getBookmarkedDocuments(Pageable pageable, String username) {
        ResponseEntity<UserDto> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserDto userDto = response.getBody();

        // Get bookmarks from PostgreSQL
        Page<DocumentBookmark> bookmarks = bookmarkRepository.findByUserId(userDto.getUserId(), pageable);

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