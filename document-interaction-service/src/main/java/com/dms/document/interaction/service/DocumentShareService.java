package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.ShareSettings;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.dto.UpdateShareSettingsRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.*;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.UserDocumentHistory;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.UserDocumentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentShareService {
    private final UserClient userClient;
    private final PublishEventService publishEventService;
    private final DocumentRepository documentRepository;
    private final DocumentPreferencesService documentPreferencesService;
    private final UserDocumentHistoryRepository userDocumentHistoryRepository;

    public ShareSettings getDocumentShareSettings(String documentId, String username) {

        DocumentInformation doc = getDocumentWithAccessCheck(documentId, username);
        return new ShareSettings(
                doc.getSharingType() == SharingType.PUBLIC,
                doc.getSharedWith()
        );
    }

    @Transactional
    public DocumentInformation updateDocumentShareSettings(
            String documentId,
            UpdateShareSettingsRequest request,
            String username) {

        DocumentInformation doc = getDocumentWithAccessCheck(documentId, username);

        // Validate shared users exist if specific sharing is requested
        if (!request.isPublic() && request.sharedWith() != null && !request.sharedWith().isEmpty()) {
            validateSharedUsers(request.sharedWith());
        }

        // Update sharing settings
        doc.setSharingType(request.isPublic() ? SharingType.PUBLIC :
                CollectionUtils.isEmpty(request.sharedWith()) ?
                        SharingType.PRIVATE : SharingType.SPECIFIC);
        doc.setSharedWith(CollectionUtils.isNotEmpty(request.sharedWith()) ?
                request.sharedWith().stream().map(UUID::toString).collect(Collectors.toSet()) :
                new HashSet<>());

        // Update audit fields
        doc.setUpdatedAt(Instant.now());
        doc.setUpdatedBy(username);

        // Save changes
        DocumentInformation updatedDoc = documentRepository.save(doc);
        updatedDoc.setContent(null);

        // Send sync event to indexing document
        CompletableFuture.runAsync(() -> {
            // Hístory
            userDocumentHistoryRepository.save(UserDocumentHistory.builder()
                    .userId(doc.getUserId())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.SHARE)
                    .version(doc.getCurrentVersion())
                    .detail(updatedDoc.getSharingType().name())
                    .createdAt(Instant.now())
                    .build());

            publishEventService.sendSyncEvent(
                    SyncEventRequest.builder()
                            .eventId(UUID.randomUUID().toString())
                            .userId(doc.getUserId())
                            .documentId(documentId)
                            .subject(EventType.UPDATE_EVENT.name())
                            .triggerAt(LocalDateTime.now())
                            .build());

            // Record sharing interaction
            if (updatedDoc.getSharingType() == SharingType.PUBLIC || updatedDoc.getSharingType() == SharingType.SPECIFIC) {
                documentPreferencesService.recordInteraction(UUID.fromString(doc.getUserId()), documentId, InteractionType.SHARE);
            }
        });

        return updatedDoc;
    }

    public List<UserResponse> searchShareableUsers(String query) {
        try {
            return userClient.searchUsers(query)
                    .getBody();
        } catch (Exception e) {
            log.error("Error searching users with query: {}", query, e);
            return List.of();
        }
    }

    public List<UserResponse> getShareableUserDetails(List<UUID> userIds) {
        try {
            return userClient.getUsersByIds(userIds)
                    .getBody();
        } catch (Exception e) {
            log.error("Error fetching user details for IDs: {}", userIds, e);
            return List.of();
        }
    }

    private DocumentInformation getDocumentWithAccessCheck(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();
        if (!(Objects.equals(userResponse.role().roleName(), AppRole.ROLE_USER) ||
              Objects.equals(userResponse.role().roleName(), AppRole.ROLE_MENTOR))) {
            throw new InvalidDataAccessResourceUsageException("Invalid role");
        }

        return documentRepository.findByIdAndUserId(documentId, userResponse.userId().toString())
                .orElseThrow(() -> new InvalidDocumentException("Document not found or access denied"));
    }

    private void validateSharedUsers(Set<UUID> userIds) {
        List<UserResponse> users = getShareableUserDetails(
                userIds.stream().toList()
        );

        if (users.size() != userIds.size()) {
            throw new InvalidDocumentException("One or more shared users do not exist");
        }
    }
}