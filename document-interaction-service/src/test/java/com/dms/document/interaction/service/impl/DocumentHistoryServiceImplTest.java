package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.DocumentStatisticsResponse;
import com.dms.document.interaction.dto.RoleResponse;
import com.dms.document.interaction.dto.UserHistoryResponse;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.model.projection.ActionCountResult;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentHistoryServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private DocumentUserHistoryRepository documentUserHistoryRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private DocumentHistoryServiceImpl documentHistoryService;

    @Captor
    private ArgumentCaptor<Query> queryCaptor;

    private UUID userId;
    private String username;
    private UserResponse userResponse;
    private ResponseEntity<UserResponse> userResponseEntity;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        username = "testuser";
        userResponse = new UserResponse(userId, username, "test@example.com", new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER));
        userResponseEntity = ResponseEntity.ok(userResponse);
    }

    @Test
    void getDocumentStatistics_shouldReturnCorrectStatistics() {
        // Arrange
        String documentId = "doc123";

        // Create action count results
        ActionCountResult viewCount = createActionCount(UserDocumentActionType.VIEW_DOCUMENT, 10);
        ActionCountResult downloadCount = createActionCount(UserDocumentActionType.DOWNLOAD_FILE, 5);
        ActionCountResult commentCount = createActionCount(UserDocumentActionType.COMMENT, 3);

        List<ActionCountResult> actionCounts = Arrays.asList(viewCount, downloadCount, commentCount);

        when(documentUserHistoryRepository.getActionCountsForDocument(documentId))
                .thenReturn(actionCounts);

        // Act
        DocumentStatisticsResponse response = documentHistoryService.getDocumentStatistics(documentId);

        // Assert
        assertNotNull(response);
        assertEquals(10, response.viewCount());
        assertEquals(5, response.downloadCount());
        assertEquals(3, response.commentCount());
        assertEquals(18, response.totalInteractions()); // Sum of all counts

        // Verify
        verify(documentUserHistoryRepository).getActionCountsForDocument(documentId);
    }

    @Test
    void getDocumentStatistics_withAllActionTypes_shouldAggregateProperly() {
        // Arrange
        String documentId = "doc123";

        // Create all possible action types
        List<ActionCountResult> actionCounts = Arrays.asList(
                createActionCount(UserDocumentActionType.VIEW_DOCUMENT, 10),
                createActionCount(UserDocumentActionType.DOWNLOAD_FILE, 5),
                createActionCount(UserDocumentActionType.DOWNLOAD_VERSION, 3),
                createActionCount(UserDocumentActionType.UPDATE_DOCUMENT, 2),
                createActionCount(UserDocumentActionType.UPDATE_DOCUMENT_FILE, 1),
                createActionCount(UserDocumentActionType.DELETE_DOCUMENT, 1),
                createActionCount(UserDocumentActionType.REVERT_VERSION, 1),
                createActionCount(UserDocumentActionType.SHARE, 4),
                createActionCount(UserDocumentActionType.FAVORITE, 6),
                createActionCount(UserDocumentActionType.COMMENT, 8)
        );

        when(documentUserHistoryRepository.getActionCountsForDocument(documentId))
                .thenReturn(actionCounts);

        // Act
        DocumentStatisticsResponse response = documentHistoryService.getDocumentStatistics(documentId);

        // Assert
        assertNotNull(response);
        assertEquals(10, response.viewCount());
        assertEquals(8, response.downloadCount()); // DOWNLOAD_FILE + DOWNLOAD_VERSION
        assertEquals(3, response.updateCount()); // UPDATE_DOCUMENT + UPDATE_DOCUMENT_FILE
        assertEquals(1, response.deleteCount());
        assertEquals(1, response.revertCount());
        assertEquals(4, response.shareCount());
        assertEquals(6, response.favoriteCount());
        assertEquals(8, response.commentCount());
        assertEquals(41, response.totalInteractions()); // Sum of all counts

        // Verify
        verify(documentUserHistoryRepository).getActionCountsForDocument(documentId);
    }

    @Test
    void getDocumentStatistics_withNoData_shouldReturnZeros() {
        // Arrange
        String documentId = "doc123";

        when(documentUserHistoryRepository.getActionCountsForDocument(documentId))
                .thenReturn(Collections.emptyList());

        // Act
        DocumentStatisticsResponse response = documentHistoryService.getDocumentStatistics(documentId);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.viewCount());
        assertEquals(0, response.downloadCount());
        assertEquals(0, response.updateCount());
        assertEquals(0, response.deleteCount());
        assertEquals(0, response.revertCount());
        assertEquals(0, response.shareCount());
        assertEquals(0, response.favoriteCount());
        assertEquals(0, response.commentCount());
        assertEquals(0, response.totalInteractions());

        // Verify
        verify(documentUserHistoryRepository).getActionCountsForDocument(documentId);
    }

    @Test
    void getUserHistory_withNoFilters_shouldReturnAllUserHistory() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);

        // Create sample histories
        List<DocumentUserHistory> histories = Arrays.asList(
                createSampleHistory(userId.toString(), "doc1", UserDocumentActionType.VIEW_DOCUMENT),
                createSampleHistory(userId.toString(), "doc2", UserDocumentActionType.DOWNLOAD_FILE)
        );

        when(mongoTemplate.find(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(histories);

        lenient().when(mongoTemplate.count(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(2L);

        // Create sample documents
        DocumentInformation doc1 = new DocumentInformation();
        doc1.setId("doc1");
        doc1.setFilename("Document 1");

        DocumentInformation doc2 = new DocumentInformation();
        doc2.setId("doc2");
        doc2.setFilename("Document 2");

        when(documentRepository.findByIdIn(anyList()))
                .thenReturn(Arrays.asList(doc1, doc2));

        // Act
        Page<UserHistoryResponse> result = documentHistoryService.getUserHistory(
                username, null, null, null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());

        // Verify query construction
        verify(userClient).getUserByUsername(username);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(DocumentUserHistory.class));
        verify(documentRepository).findByIdIn(anyList());

        // The query should contain the userId criteria
        Query capturedQuery = queryCaptor.getValue();
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("userId"));
        assertTrue(queryString.contains(userId.toString()));
    }

    @Test
    void getUserHistory_withActionTypeFilter_shouldFilterByAction() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        UserDocumentActionType actionType = UserDocumentActionType.VIEW_DOCUMENT;

        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);

        // Create sample histories
        List<DocumentUserHistory> histories = Collections.singletonList(
                createSampleHistory(userId.toString(), "doc1", UserDocumentActionType.VIEW_DOCUMENT)
        );

        when(mongoTemplate.find(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(histories);

        // Create sample documents
        DocumentInformation doc1 = new DocumentInformation();
        doc1.setId("doc1");
        doc1.setFilename("Document 1");

        when(documentRepository.findByIdIn(anyList()))
                .thenReturn(Collections.singletonList(doc1));

        // Count is used internally by PageableExecutionUtils
        lenient().when(mongoTemplate.count(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(1L);

        // Act
        Page<UserHistoryResponse> result = documentHistoryService.getUserHistory(
                username, actionType, null, null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(UserDocumentActionType.VIEW_DOCUMENT, result.getContent().get(0).actionType());

        // Verify query includes action type filter
        verify(mongoTemplate).find(queryCaptor.capture(), eq(DocumentUserHistory.class));
        Query capturedQuery = queryCaptor.getValue();
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("userDocumentActionType"));
        assertTrue(queryString.contains(actionType.name()));
    }

    @Test
    void getUserHistory_withDateFilters_shouldFilterByDate() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Instant fromDate = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant toDate = Instant.now();

        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);

        // Create sample histories
        List<DocumentUserHistory> histories = Arrays.asList(
                createSampleHistory(userId.toString(), "doc1", UserDocumentActionType.VIEW_DOCUMENT),
                createSampleHistory(userId.toString(), "doc2", UserDocumentActionType.DOWNLOAD_FILE)
        );

        when(mongoTemplate.find(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(histories);

        lenient().when(mongoTemplate.count(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(2L);

        // Create sample documents
        DocumentInformation doc1 = new DocumentInformation();
        doc1.setId("doc1");
        doc1.setFilename("Document 1");

        DocumentInformation doc2 = new DocumentInformation();
        doc2.setId("doc2");
        doc2.setFilename("Document 2");

        when(documentRepository.findByIdIn(anyList()))
                .thenReturn(Arrays.asList(doc1, doc2));

        // Act
        Page<UserHistoryResponse> result = documentHistoryService.getUserHistory(
                username, null, fromDate, toDate, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());

        // Verify date filters are included in query
        verify(mongoTemplate).find(queryCaptor.capture(), eq(DocumentUserHistory.class));
        Query capturedQuery = queryCaptor.getValue();
        String queryString = capturedQuery.toString();
        assertTrue(queryString.contains("createdAt"));
    }

    @Test
    void getUserHistory_withSearchTerm_shouldFilterByTitleOrDetail() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        String searchTerm = "Document";

        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);

        // Mock document ID search - use lenient() to avoid unnecessary stubbing error
        lenient().when(mongoTemplate.find(any(Query.class), eq(org.bson.Document.class), eq("documents")))
                .thenReturn(Arrays.asList(
                        new org.bson.Document("_id", "doc1"),
                        new org.bson.Document("_id", "doc2")
                ));

        // Create sample histories
        List<DocumentUserHistory> histories = Arrays.asList(
                createSampleHistory(userId.toString(), "doc1", UserDocumentActionType.VIEW_DOCUMENT),
                createSampleHistory(userId.toString(), "doc2", UserDocumentActionType.DOWNLOAD_FILE)
        );

        when(mongoTemplate.find(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(histories);

        lenient().when(mongoTemplate.count(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(2L);

        // Create sample documents
        DocumentInformation doc1 = new DocumentInformation();
        doc1.setId("doc1");
        doc1.setFilename("Document 1");

        DocumentInformation doc2 = new DocumentInformation();
        doc2.setId("doc2");
        doc2.setFilename("Document 2");

        when(documentRepository.findByIdIn(anyList()))
                .thenReturn(Arrays.asList(doc1, doc2));

        // Act
        Page<UserHistoryResponse> result = documentHistoryService.getUserHistory(
                username, null, null, null, searchTerm, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());

        // Verify
        verify(userClient).getUserByUsername(username);
        verify(mongoTemplate).find(any(Query.class), eq(DocumentUserHistory.class));
    }

    @Test
    void getUserHistory_withSomeDeletedDocuments_shouldHandleGracefully() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);

        // Create sample histories for 3 documents
        List<DocumentUserHistory> histories = Arrays.asList(
                createSampleHistory(userId.toString(), "doc1", UserDocumentActionType.VIEW_DOCUMENT),
                createSampleHistory(userId.toString(), "doc2", UserDocumentActionType.DOWNLOAD_FILE),
                createSampleHistory(userId.toString(), "doc3", UserDocumentActionType.COMMENT)
        );

        when(mongoTemplate.find(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(histories);

        lenient().when(mongoTemplate.count(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(3L);

        // Only return 2 documents (one is deleted or not found)
        DocumentInformation doc1 = new DocumentInformation();
        doc1.setId("doc1");
        doc1.setFilename("Document 1");

        DocumentInformation doc2 = new DocumentInformation();
        doc2.setId("doc2");
        doc2.setFilename("Document 2");

        // Respond with any list of document IDs - order doesn't matter
        when(documentRepository.findByIdIn(anyList()))
                .thenReturn(Arrays.asList(doc1, doc2));

        // Act
        Page<UserHistoryResponse> result = documentHistoryService.getUserHistory(
                username, null, null, null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getTotalElements()); // Total count from database
        assertEquals(3, result.getContent().size()); // All histories included

        // Verify document ID retrieval without specifying exact order
        verify(documentRepository).findByIdIn(anyList());

        // Check document titles in response
        HashSet<String> documentTitles = new HashSet<>();
        result.getContent().forEach(history -> documentTitles.add(history.documentTitle()));

        assertTrue(documentTitles.contains("Document 1"));
        assertTrue(documentTitles.contains("Document 2"));
        assertTrue(documentTitles.contains("Unknown Document"));
    }

    @Test
    void getUserHistory_withNoResults_shouldReturnEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);

        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);

        // Mock mongoTemplate find operation with empty results
        when(mongoTemplate.find(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(Collections.emptyList());

        lenient().when(mongoTemplate.count(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(0L);

        // Act
        Page<UserHistoryResponse> result = documentHistoryService.getUserHistory(
                username, null, null, null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());

        // Verify
        verify(userClient).getUserByUsername(username);
        verify(mongoTemplate).find(any(Query.class), eq(DocumentUserHistory.class));
        verify(documentRepository, never()).findByIdIn(anyList());
    }

    @Test
    void getUserHistory_whenUserNotFound_shouldThrowException() {
        // Arrange
        String nonExistentUsername = "nonExistentUser";
        Pageable pageable = PageRequest.of(0, 10);

        // Mock user client to return not found
        when(userClient.getUserByUsername(nonExistentUsername))
                .thenReturn(ResponseEntity.notFound().build());

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentHistoryService.getUserHistory(nonExistentUsername, null, null, null, null, pageable)
        );

        // Verify
        verify(userClient).getUserByUsername(nonExistentUsername);
        verifyNoInteractions(mongoTemplate, documentRepository);
    }

    @Test
    void getUserHistory_withPagination_shouldApplyPageable() {
        // Arrange
        int page = 2;
        int size = 5;
        Pageable pageable = PageRequest.of(page, size);

        when(userClient.getUserByUsername(username)).thenReturn(userResponseEntity);

        // Mock empty results for simplicity
        when(mongoTemplate.find(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(Collections.emptyList());

        // Count is used internally by PageableExecutionUtils
        when(mongoTemplate.count(any(Query.class), eq(DocumentUserHistory.class)))
                .thenReturn(0L);

        // Act
        documentHistoryService.getUserHistory(username, null, null, null, null, pageable);

        // Verify
        verify(mongoTemplate).find(queryCaptor.capture(), eq(DocumentUserHistory.class));

        // Check if the query has pagination
        Query capturedQuery = queryCaptor.getValue();
        assertNotNull(capturedQuery);
    }

    // Helper methods
    private DocumentUserHistory createSampleHistory(String userId, String documentId, UserDocumentActionType actionType) {
        return DocumentUserHistory.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .documentId(documentId)
                .userDocumentActionType(actionType)
                .createdAt(Instant.now())
                .build();
    }

    private ActionCountResult createActionCount(UserDocumentActionType type, int count) {
        ActionCountResult result = new ActionCountResult();
        result.setActionType(type.name());
        result.setCount(count);
        return result;
    }
}