package com.dms.document.search.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dms.document.search.client.UserClient;
import com.dms.document.search.dto.DocumentSearchCriteria;
import com.dms.document.search.dto.RoleResponse;
import com.dms.document.search.dto.UserResponse;
import com.dms.document.search.enums.AppRole;
import com.dms.document.search.enums.DocumentStatus;
import com.dms.document.search.enums.SharingType;
import com.dms.document.search.model.DocumentInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private DocumentService documentService;

    private UUID userId;
    private String username;
    private UserResponse userResponse;
    private DocumentSearchCriteria criteria;
    private List<DocumentInformation> documentList;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        username = "testuser";
        userResponse = new UserResponse(userId, username, "test@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER));

        criteria = DocumentSearchCriteria.builder()
                .search("test")
                .majors(Set.of("Computer Science"))
                .courseCodes(Set.of("CS101"))
                .level("Undergraduate")
                .categories(Set.of("Assignment"))
                .tags(Set.of("Java"))
                .sortField("createdAt")
                .sortDirection("DESC")
                .build();

        DocumentInformation doc1 = DocumentInformation.builder()
                .id("doc1")
                .filename("Test Document 1")
                .status(DocumentStatus.COMPLETED)
                .userId(userId.toString())
                .sharingType(SharingType.PRIVATE)
                .createdAt(Instant.now())
                .build();

        DocumentInformation doc2 = DocumentInformation.builder()
                .id("doc2")
                .filename("Test Document 2")
                .status(DocumentStatus.COMPLETED)
                .userId(userId.toString())
                .sharingType(SharingType.PRIVATE)
                .createdAt(Instant.now())
                .build();

        documentList = List.of(doc1, doc2);
    }

    @Test
    void getUserDocuments_ValidUser_ReturnsDocuments() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(mongoTemplate.count(any(Query.class), eq(DocumentInformation.class))).thenReturn(2L);
        when(mongoTemplate.find(any(Query.class), eq(DocumentInformation.class))).thenReturn(documentList);

        // Act
        Page<DocumentInformation> result = documentService.getUserDocuments(username, criteria, 0, 10);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        verify(userClient, times(1)).getUserByUsername(username);
        verify(mongoTemplate, times(1)).count(any(Query.class), eq(DocumentInformation.class));
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(DocumentInformation.class));
    }

    @Test
    void getUserDocuments_UserNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.notFound().build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () -> {
            documentService.getUserDocuments(username, criteria, 0, 10);
        });
        verify(userClient, times(1)).getUserByUsername(username);
        verify(mongoTemplate, never()).count(any(Query.class), eq(DocumentInformation.class));
        verify(mongoTemplate, never()).find(any(Query.class), eq(DocumentInformation.class));
    }

    @Test
    void getUserDocuments_InvalidRole_ThrowsException() {
        // Arrange
        UserResponse adminUser = new UserResponse(userId, username, "test@example.com",
                new RoleResponse(UUID.randomUUID(), AppRole.valueOf("ROLE_ADMIN")));
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(adminUser));

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () -> {
            documentService.getUserDocuments(username, criteria, 0, 10);
        });
        verify(userClient, times(1)).getUserByUsername(username);
        verify(mongoTemplate, never()).count(any(Query.class), eq(DocumentInformation.class));
        verify(mongoTemplate, never()).find(any(Query.class), eq(DocumentInformation.class));
    }

    @Test
    void getUserDocuments_NoFilters_BuildsBasicQuery() {
        // Arrange
        DocumentSearchCriteria emptyCriteria = DocumentSearchCriteria.builder()
                .sortField("createdAt")
                .sortDirection("DESC")
                .build();

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(mongoTemplate.count(any(Query.class), eq(DocumentInformation.class))).thenReturn(2L);
        when(mongoTemplate.find(any(Query.class), eq(DocumentInformation.class))).thenReturn(documentList);

        // Act
        Page<DocumentInformation> result = documentService.getUserDocuments(username, emptyCriteria, 0, 10);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(DocumentInformation.class));
    }
}