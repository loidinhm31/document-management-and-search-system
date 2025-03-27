package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.ShareSettings;
import com.dms.document.interaction.dto.SyncEventRequest;
import com.dms.document.interaction.dto.UpdateShareSettingsRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.*;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import com.dms.document.interaction.service.DocumentPreferencesService;
import com.dms.document.interaction.service.DocumentShareService;
import com.dms.document.interaction.service.PublishEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentShareServiceImpl implements DocumentShareService {
    private final UserClient userClient;
    private final PublishEventService publishEventService;
    private final DocumentRepository documentRepository;
    private final DocumentPreferencesService documentPreferencesService;
    private final DocumentUserHistoryRepository documentUserHistoryRepository;

    @Value("${app.auth-service.api-key}")
    private String serviceApiKey;

    @Override
    public ShareSettings getDocumentShareSettings(String documentId, String username) {

        DocumentInformation doc = getDocumentWithAccessCheck(documentId, username);
        return new ShareSettings(
                doc.getSharingType() == SharingType.PUBLIC,
                doc.getSharedWith()
        );
    }

    @Override
    @Transactional
    public DocumentInformation updateDocumentShareSettings(
            String documentId,
            UpdateShareSettingsRequest request,
            String username) {

        DocumentInformation doc = getDocumentWithAccessCheck(documentId, username);

        // Validate shared users exist if specific sharing is requested
        List<UserResponse> userDetailsForHistory = new ArrayList<>();
        if (!request.isPublic() && Objects.nonNull(request.sharedWith()) && CollectionUtils.isNotEmpty(request.sharedWith())) {
            // Fetch user details
            if (CollectionUtils.isNotEmpty(request.sharedWith())) {
                ResponseEntity<List<UserResponse>> usersResponse = userClient.getUsersByIds(new ArrayList<>(request.sharedWith()));
                if (usersResponse.hasBody()) {
                    userDetailsForHistory = usersResponse.getBody();
                }
            }
            validateSharedUsers(request.sharedWith(), userDetailsForHistory);
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


        final List<UserResponse> finalUserDetails = userDetailsForHistory;

        // Send sync event to indexing document
        CompletableFuture.runAsync(() -> {
            // History
            StringBuilder sharingDetail = new StringBuilder();
            sharingDetail.append(doc.getSharingType());

            if (CollectionUtils.isNotEmpty(finalUserDetails)) {
                String sharedWith = String.join(", ", finalUserDetails.stream()
                        .map(UserResponse::username).toList());
                sharingDetail.append(" - ").append(sharedWith);
            }

            documentUserHistoryRepository.save(DocumentUserHistory.builder()
                    .userId(doc.getUserId())
                    .documentId(documentId)
                    .userDocumentActionType(UserDocumentActionType.SHARE)
                    .version(doc.getCurrentVersion())
                    .detail(sharingDetail.toString())
                    .createdAt(Instant.now())
                    .build());

            publishEventService.sendSyncEvent(
                    SyncEventRequest.builder()
                            .eventId(UUID.randomUUID().toString())
                            .userId(doc.getUserId())
                            .documentId(documentId)
                            .subject(EventType.UPDATE_EVENT.name())
                            .triggerAt(Instant.now())
                            .build());

            // Record sharing interaction
            if (updatedDoc.getSharingType() == SharingType.PUBLIC || updatedDoc.getSharingType() == SharingType.SPECIFIC) {
                documentPreferencesService.recordInteraction(UUID.fromString(doc.getUserId()), documentId, InteractionType.SHARE);
            }
        });

        return updatedDoc;
    }

    @Override
    public List<UserResponse> searchShareableUsers(String query, String username) {
        try {
            ResponseEntity<List<UserResponse>> usersResponse = userClient.searchUsers(query);
            if (!usersResponse.hasBody()) {
                return List.of();
            }
            List<UserResponse> users = usersResponse.getBody();
            if (CollectionUtils.isNotEmpty(users)) {
                users.removeIf(user -> user.username().equals(username));
            }
            return users;
        } catch (Exception e) {
            log.error("Error searching users with query: {}", query, e);
            return List.of();
        }
    }

    @Override
    public List<UserResponse> getShareableUserDetails(List<UUID> userIds) {
        try {
            ResponseEntity<List<UserResponse>> response = userClient.getUsersByIds(userIds);
            return response.getBody() != null ? response.getBody() : List.of();
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

    private void validateSharedUsers(Set<UUID> userIds, List<UserResponse> users) {
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        if (users.size() != userIds.size()) {
            Set<UUID> foundUserIds = users.stream()
                    .map(UserResponse::userId)
                    .collect(Collectors.toSet());

            Set<UUID> missingUserIds = new HashSet<>(userIds);
            missingUserIds.removeAll(foundUserIds);

            String errorMessage = "One or more shared users do not exist";
            if (!missingUserIds.isEmpty()) {
                errorMessage += ": " + String.join(", ",
                        missingUserIds.stream().map(UUID::toString).toList());
            }

            throw new InvalidDocumentException(errorMessage);
        }
    }
}