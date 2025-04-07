package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.DocumentReportStatus;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.mapper.ReportTypeMapper;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentReport;
import com.dms.document.interaction.model.MasterData;
import com.dms.document.interaction.model.Translation;
import com.dms.document.interaction.model.projection.DocumentReportProjection;
import com.dms.document.interaction.repository.DocumentReportRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.MasterDataRepository;
import com.dms.document.interaction.service.PublishEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import java.util.Collection;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentReportServiceImplTest {
    private static final String NL = System.lineSeparator();

    @Mock
    private DocumentReportRepository documentReportRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private MasterDataRepository masterDataRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private PublishEventService publishEventService;

    @Mock
    private ReportTypeMapper reportTypeMapper;

    @InjectMocks
    private DocumentReportServiceImpl documentReportService;

    @Captor
    private ArgumentCaptor<DocumentReport> reportCaptor;

    @Captor
    private ArgumentCaptor<SyncEventRequest> syncEventCaptor;

    @Captor
    private ArgumentCaptor<DocumentInformation> documentCaptor;

    @Captor
    private ArgumentCaptor<List<DocumentReport>> reportsCaptor;

    private final String documentId = "doc123";
    private final String username = "testuser";
    private final UUID userId = UUID.randomUUID();
    private UserResponse userResponse;
    private DocumentInformation documentInformation;
    private MasterData reportType;
    private DocumentReport documentReport;
    private ReportResponse reportResponse;
    private ReportTypeResponse reportTypeResponse;

    @BeforeEach
    void setUp() {
        // Setup user response
        RoleResponse roleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER);
        userResponse = new UserResponse(userId, username, "test@example.com", roleResponse);

        // Setup admin response
        RoleResponse adminRoleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        UserResponse adminResponse = new UserResponse(UUID.randomUUID(), "admin", "admin@example.com", adminRoleResponse);

        // Setup document information
        documentInformation = DocumentInformation.builder()
                .id(documentId)
                .userId(userId.toString())
                .filename("Test Document")
                .build();

        // Setup report type
        reportType = new MasterData();
        reportType.setId("rt1");
        reportType.setCode("COPYRIGHT");
        reportType.setType(MasterDataType.REPORT_DOCUMENT_TYPE);
        Translation translation = new Translation();
        translation.setEn("Copyright Violation");
        translation.setVi("Vi phạm bản quyền");
        reportType.setTranslations(translation);

        // Setup document report
        documentReport = new DocumentReport();
        documentReport.setId(1L);
        documentReport.setDocumentId(documentId);
        documentReport.setUserId(userId);
        documentReport.setReportTypeCode("COPYRIGHT");
        documentReport.setDescription("This document violates copyright");
        documentReport.setStatus(DocumentReportStatus.PENDING);
        documentReport.setProcessed(false);
        documentReport.setCreatedAt(Instant.now());
        documentReport.setTimes(1);

        // Setup report response
        TranslationDTO translationDTO = new TranslationDTO();
        translationDTO.setEn("Copyright Violation");
        translationDTO.setVi("Vi phạm bản quyền");
        reportResponse = new ReportResponse(
                1L,
                documentId,
                "COPYRIGHT",
                translationDTO,
                "This document violates copyright",
                Instant.now()
        );

        // Setup report type response
        reportTypeResponse = new ReportTypeResponse(
                "COPYRIGHT",
                translationDTO,
                "Reports for copyright violation"
        );

        // Removed default mocks from setup to avoid UnnecessaryStubbingException
        // These will be configured in individual test methods as needed
    }

    @Test
    void createReport_Success() {
        // Arrange
        ReportRequest request = new ReportRequest("COPYRIGHT", "This document violates copyright");

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userId.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_DOCUMENT_TYPE, "COPYRIGHT"))
                .thenReturn(Optional.of(reportType));
        when(documentReportRepository.existsByDocumentIdAndUserIdAndProcessed(documentId, userId, false))
                .thenReturn(false);
        when(documentReportRepository.findByDocumentIdAndProcessed(documentId, true))
                .thenReturn(Collections.emptyList());
        when(documentReportRepository.save(any(DocumentReport.class))).thenReturn(documentReport);
        when(reportTypeMapper.mapToResponse(any(DocumentReport.class), any(MasterData.class)))
                .thenReturn(reportResponse);

        // Act
        ReportResponse result = documentReportService.createReport(documentId, request, username);

        // Assert
        assertNotNull(result);
        assertEquals(documentId, result.documentId());
        assertEquals("COPYRIGHT", result.reportTypeCode());

        // Verify the report was saved with correct values
        verify(documentReportRepository).save(reportCaptor.capture());
        DocumentReport savedReport = reportCaptor.getValue();
        assertEquals(documentId, savedReport.getDocumentId());
        assertEquals(userId, savedReport.getUserId());
        assertEquals("COPYRIGHT", savedReport.getReportTypeCode());
        assertEquals("This document violates copyright", savedReport.getDescription());
        assertEquals(DocumentReportStatus.PENDING, savedReport.getStatus());
        assertFalse(savedReport.getProcessed());
        assertEquals(1, savedReport.getTimes());
    }

    @Test
    void createReport_DocumentNotAccessible_ThrowsException() {
        // Arrange
        ReportRequest request = new ReportRequest("COPYRIGHT", "This document violates copyright");

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userId.toString()))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                documentReportService.createReport(documentId, request, username)
        );
        assertEquals("Document not found or not accessible", exception.getMessage());

        // Verify no report was saved
        verify(documentReportRepository, never()).save(any(DocumentReport.class));
    }

    @Test
    void createReport_InvalidReportType_ThrowsException() {
        // Arrange
        ReportRequest request = new ReportRequest("INVALID_TYPE", "This document violates copyright");

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userId.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_DOCUMENT_TYPE, "INVALID_TYPE"))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                documentReportService.createReport(documentId, request, username)
        );
        assertEquals("Invalid report type", exception.getMessage());

        // Verify no report was saved
        verify(documentReportRepository, never()).save(any(DocumentReport.class));
    }

    @Test
    void createReport_AlreadyReported_ThrowsException() {
        // Arrange
        ReportRequest request = new ReportRequest("COPYRIGHT", "This document violates copyright");

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userId.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_DOCUMENT_TYPE, "COPYRIGHT"))
                .thenReturn(Optional.of(reportType));
        when(documentReportRepository.existsByDocumentIdAndUserIdAndProcessed(documentId, userId, false))
                .thenReturn(true);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                documentReportService.createReport(documentId, request, username)
        );
        assertEquals("You have already reported this document", exception.getMessage());

        // Verify no report was saved
        verify(documentReportRepository, never()).save(any(DocumentReport.class));
    }

    @Test
    void createReport_WithExistingProcessedReport_IncrementsCounter() {
        // Arrange
        ReportRequest request = new ReportRequest("COPYRIGHT", "This document violates copyright");

        // Create a processed report with times = 2
        DocumentReport processedReport = new DocumentReport();
        processedReport.setTimes(2);
        processedReport.setProcessed(true);

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userId.toString()))
                .thenReturn(Optional.of(documentInformation));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_DOCUMENT_TYPE, "COPYRIGHT"))
                .thenReturn(Optional.of(reportType));
        when(documentReportRepository.existsByDocumentIdAndUserIdAndProcessed(documentId, userId, false))
                .thenReturn(false);
        when(documentReportRepository.findByDocumentIdAndProcessed(documentId, true))
                .thenReturn(Collections.singletonList(processedReport));
        when(documentReportRepository.save(any(DocumentReport.class))).thenReturn(documentReport);
        when(reportTypeMapper.mapToResponse(any(DocumentReport.class), any(MasterData.class)))
                .thenReturn(reportResponse);

        // Act
        documentReportService.createReport(documentId, request, username);

        // Verify the report was saved with incremented times value
        verify(documentReportRepository).save(reportCaptor.capture());
        DocumentReport savedReport = reportCaptor.getValue();
        assertEquals(3, savedReport.getTimes()); // Should be prior value + 1
    }

    @Test
    void updateReportStatus_Success() {
        // Arrange
        RoleResponse adminRoleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        UUID adminId = UUID.randomUUID();
        UserResponse adminResponse = new UserResponse(adminId, "admin", "admin@example.com", adminRoleResponse);

        // Use Mockito.spy to make the actual implementation of CompletableFuture.runAsync testable
        try (MockedStatic<CompletableFuture> mockedStatic = Mockito.mockStatic(CompletableFuture.class)) {
            // Create a mock CompletableFuture
            CompletableFuture<Void> mockFuture = CompletableFuture.completedFuture(null);

            // Make CompletableFuture.runAsync immediately run the Runnable and return our mock
            mockedStatic.when(() -> CompletableFuture.runAsync(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run(); // Execute the runnable immediately
                        return mockFuture;
                    });

            when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

            DocumentReport pendingReport = new DocumentReport();
            pendingReport.setDocumentId(documentId);
            pendingReport.setStatus(DocumentReportStatus.PENDING);
            pendingReport.setProcessed(false);
            pendingReport.setTimes(3);
            pendingReport.setCreatedAt(Instant.now());

            List<DocumentReport> pendingReports = Collections.singletonList(pendingReport);

            when(documentReportRepository.findByDocumentIdAndProcessed(documentId, false))
                    .thenReturn(pendingReports);
            when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));

            // Act
            documentReportService.updateReportStatus(documentId, DocumentReportStatus.RESOLVED, "admin");

            // Assert
            verify(documentReportRepository).saveAll(anyList());
            verify(documentRepository).save(any(DocumentInformation.class));

            // Now this should work because the Runnable passed to CompletableFuture.runAsync was executed immediately
            verify(publishEventService).sendSyncEvent(syncEventCaptor.capture());

            // Verify report status was updated correctly
            assertEquals(DocumentReportStatus.RESOLVED, pendingReport.getStatus());
            assertEquals(adminId, pendingReport.getUpdatedBy());
            assertNotNull(pendingReport.getUpdatedAt());
            assertFalse(pendingReport.getProcessed());

            // Verify the sync event properties
            SyncEventRequest capturedEvent = syncEventCaptor.getValue();
            assertNotNull(capturedEvent);
            assertEquals(EventType.DOCUMENT_REPORT_PROCESS_EVENT.name(), capturedEvent.getSubject());
            assertEquals(documentId, capturedEvent.getDocumentId());
            assertEquals(3, capturedEvent.getVersionNumber()); // Should match the times value
        }
    }

    @Test
    void updateReportStatus_RejectStatus_MarksReportAsProcessed() {
        // Arrange
        RoleResponse adminRoleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        UUID adminId = UUID.randomUUID();
        UserResponse adminResponse = new UserResponse(adminId, "admin", "admin@example.com", adminRoleResponse);

        try (MockedStatic<CompletableFuture> mockedStatic = Mockito.mockStatic(CompletableFuture.class)) {
            CompletableFuture<Void> mockFuture = CompletableFuture.completedFuture(null);
            mockedStatic.when(() -> CompletableFuture.runAsync(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run();
                        return mockFuture;
                    });

            when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

            DocumentReport pendingReport = new DocumentReport();
            pendingReport.setDocumentId(documentId);
            pendingReport.setStatus(DocumentReportStatus.PENDING);
            pendingReport.setProcessed(false);
            pendingReport.setTimes(2);
            pendingReport.setCreatedAt(Instant.now());

            List<DocumentReport> pendingReports = Collections.singletonList(pendingReport);

            when(documentReportRepository.findByDocumentIdAndProcessed(documentId, false))
                    .thenReturn(pendingReports);
            when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));

            // Act
            documentReportService.updateReportStatus(documentId, DocumentReportStatus.REJECTED, "admin");

            // Assert
            verify(documentReportRepository).saveAll(anyList());
            verify(documentRepository).save(documentCaptor.capture());

            // Verify document report status was updated correctly
            assertEquals(DocumentReportStatus.REJECTED, pendingReport.getStatus());
            assertEquals(adminId, pendingReport.getUpdatedBy());
            assertTrue(pendingReport.getProcessed()); // Should be marked as processed for REJECTED status

            // Verify document status was updated
            DocumentInformation updatedDoc = documentCaptor.getValue();
            assertEquals(DocumentReportStatus.REJECTED, updatedDoc.getReportStatus());
        }
    }

    @Test
    void updateReportStatus_RemediatedStatus_MarksReportAsProcessed() {
        // Arrange
        RoleResponse adminRoleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        UUID adminId = UUID.randomUUID();
        UserResponse adminResponse = new UserResponse(adminId, "admin", "admin@example.com", adminRoleResponse);

        try (MockedStatic<CompletableFuture> mockedStatic = Mockito.mockStatic(CompletableFuture.class)) {
            CompletableFuture<Void> mockFuture = CompletableFuture.completedFuture(null);
            mockedStatic.when(() -> CompletableFuture.runAsync(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run();
                        return mockFuture;
                    });

            when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

            DocumentReport pendingReport = new DocumentReport();
            pendingReport.setDocumentId(documentId);
            pendingReport.setStatus(DocumentReportStatus.PENDING);
            pendingReport.setProcessed(false);
            pendingReport.setTimes(2);
            pendingReport.setCreatedAt(Instant.now());

            List<DocumentReport> pendingReports = Collections.singletonList(pendingReport);

            when(documentReportRepository.findByDocumentIdAndProcessed(documentId, false))
                    .thenReturn(pendingReports);
            when(documentRepository.findById(documentId)).thenReturn(Optional.of(documentInformation));

            // Act
            documentReportService.updateReportStatus(documentId, DocumentReportStatus.REMEDIATED, "admin");

            // Assert
            verify(documentReportRepository).saveAll(anyList());
            verify(documentRepository).save(documentCaptor.capture());

            // Verify document report status was updated correctly
            assertEquals(DocumentReportStatus.REMEDIATED, pendingReport.getStatus());
            assertEquals(adminId, pendingReport.getUpdatedBy());
            assertTrue(pendingReport.getProcessed()); // Should be marked as processed for REMEDIATED status

            // Verify document status was updated
            DocumentInformation updatedDoc = documentCaptor.getValue();
            assertEquals(DocumentReportStatus.REMEDIATED, updatedDoc.getReportStatus());
        }
    }

    @Test
    void updateReportStatus_NotAdmin_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        // User is not an admin (already set up in the setUp method)

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                documentReportService.updateReportStatus(documentId, DocumentReportStatus.RESOLVED, username)
        );
        assertEquals("Only administrators can update report status", exception.getMessage());

        // Verify no updates were made
        verify(documentReportRepository, never()).save(any(DocumentReport.class));
        verify(documentRepository, never()).save(any(DocumentInformation.class));
    }

    @Test
    void updateReportStatus_NoReports_ThrowsException() {
        // Arrange
        RoleResponse adminRoleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        UserResponse adminResponse = new UserResponse(UUID.randomUUID(), "admin", "admin@example.com", adminRoleResponse);
        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

        when(documentReportRepository.findByDocumentIdAndProcessed(documentId, false))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                documentReportService.updateReportStatus(documentId, DocumentReportStatus.RESOLVED, "admin")
        );
        assertEquals("Report not found", exception.getMessage());
    }

    @Test
    void updateReportStatus_AlreadyProcessed_ThrowsException() {
        // Arrange
        RoleResponse adminRoleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        UserResponse adminResponse = new UserResponse(UUID.randomUUID(), "admin", "admin@example.com", adminRoleResponse);
        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

        DocumentReport processedReport = new DocumentReport();
        processedReport.setStatus(DocumentReportStatus.REJECTED);
        processedReport.setProcessed(true);

        when(documentReportRepository.findByDocumentIdAndProcessed(documentId, false))
                .thenReturn(Collections.singletonList(processedReport));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                documentReportService.updateReportStatus(documentId, DocumentReportStatus.RESOLVED, "admin")
        );
        assertEquals("Report has already been processed", exception.getMessage());
    }

    @Test
    void updateReportStatus_SameStatus_ThrowsException() {
        // Arrange
        RoleResponse adminRoleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        UserResponse adminResponse = new UserResponse(UUID.randomUUID(), "admin", "admin@example.com", adminRoleResponse);
        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

        DocumentReport pendingReport = new DocumentReport();
        pendingReport.setStatus(DocumentReportStatus.PENDING);
        pendingReport.setProcessed(false);

        when(documentReportRepository.findByDocumentIdAndProcessed(documentId, false))
                .thenReturn(Collections.singletonList(pendingReport));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                documentReportService.updateReportStatus(documentId, DocumentReportStatus.PENDING, "admin")
        );
        assertEquals("Cannot update the same status", exception.getMessage());
    }

    @Test
    void getUserReport_Found_ReturnsReport() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentReportRepository.findByDocumentIdAndUserIdAndProcessed(documentId, userId, false))
                .thenReturn(Optional.of(documentReport));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_DOCUMENT_TYPE, "COPYRIGHT"))
                .thenReturn(Optional.of(reportType));
        when(reportTypeMapper.mapToResponse(documentReport, reportType))
                .thenReturn(reportResponse);

        // Act
        Optional<ReportResponse> result = documentReportService.getUserReport(documentId, username);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(documentId, result.get().documentId());
        assertEquals("COPYRIGHT", result.get().reportTypeCode());
    }

    @Test
    void getUserReport_NotFound_ReturnsEmptyOptional() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentReportRepository.findByDocumentIdAndUserIdAndProcessed(documentId, userId, false))
                .thenReturn(Optional.empty());

        // Act
        Optional<ReportResponse> result = documentReportService.getUserReport(documentId, username);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getReportTypes_ReturnsAllTypes() {
        // Arrange
        List<MasterData> reportTypes = Collections.singletonList(reportType);

        when(masterDataRepository.findByTypeAndIsActive(MasterDataType.REPORT_DOCUMENT_TYPE, true))
                .thenReturn(reportTypes);
        when(reportTypeMapper.mapToReportTypeResponse(reportType))
                .thenReturn(reportTypeResponse);

        // Act
        List<ReportTypeResponse> result = documentReportService.getReportTypes();

        // Assert
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("COPYRIGHT", result.get(0).code());
    }

    @Test
    void getAdminDocumentReports_WithFilters_ReturnsFilteredReports() {
        // Arrange
        String documentTitle = "Test Document";
        String uploaderUsername = "testUploader";
        DocumentReportStatus status = DocumentReportStatus.PENDING;
        Instant fromDate = Instant.now().minus(java.time.Duration.ofDays(7));
        Instant toDate = Instant.now();
        String reportTypeCode = "COPYRIGHT";
        Pageable pageable = mock(Pageable.class);

        // Create expected document information
        DocumentInformation documentInfo = DocumentInformation.builder()
                .id(documentId)
                .filename("Test Document")
                .userId(UUID.randomUUID().toString())
                .build();

        // Mock searching for users by username
        UUID uploaderUserId = UUID.randomUUID();
        UserResponse uploaderUser = new UserResponse(uploaderUserId, uploaderUsername, "uploader@example.com", null);
        List<UserResponse> searchResults = Collections.singletonList(uploaderUser);

        when(userClient.searchUsers(uploaderUsername))
                .thenReturn(ResponseEntity.ok(searchResults));

        // Mock finding documents by uploader ID and title
        when(documentRepository.findByFilenameLikeIgnoreCaseAndUserId(documentTitle, uploaderUserId.toString()))
                .thenReturn(Collections.singletonList(documentInfo));

        // Mock projection
        DocumentReportProjection projection = mock(DocumentReportProjection.class);
        when(projection.getDocumentId()).thenReturn(documentId);
        when(projection.getStatus()).thenReturn(DocumentReportStatus.PENDING);
        when(projection.getProcessed()).thenReturn(false);
        when(projection.getReportCount()).thenReturn(1);
        when(projection.getUpdatedBy()).thenReturn(null);
        when(projection.getUpdatedAt()).thenReturn(null);

        List<DocumentReportProjection> projections = Collections.singletonList(projection);
        Page<DocumentReportProjection> projectionPage = new PageImpl<>(projections, pageable, 1);

        // Set up a capture for the document IDs collection
        ArgumentCaptor<Collection<String>> docIdsCaptor = ArgumentCaptor.forClass(Collection.class);

        // Mock repository call with filtered document IDs
        when(documentReportRepository.findDocumentReportsGroupedByProcessedAndDocumentIds(
                eq(status.name()),
                eq(fromDate),
                eq(toDate),
                eq(reportTypeCode),
                docIdsCaptor.capture(),
                eq(pageable)))
                .thenReturn(projectionPage);

        // Mock finding document by ID
        when(documentRepository.findAllById(anyList()))
                .thenReturn(Collections.singletonList(documentInfo));

        // Mock username retrieval
        when(userClient.getUsersByIds(anyList())).thenReturn(ResponseEntity.ok(Collections.emptyList()));

        // Act
        Page<AdminDocumentReportResponse> result = documentReportService.getAdminDocumentReports(
                documentTitle, uploaderUsername, fromDate, toDate, status, reportTypeCode, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size()); // Make sure we have one item
        assertEquals(documentId, result.getContent().get(0).documentId());
        assertEquals(DocumentReportStatus.PENDING, result.getContent().get(0).status());

        // Verify the document IDs passed to the repository method
        Collection<String> capturedDocIds = docIdsCaptor.getValue();
        assertTrue(capturedDocIds.contains(documentId));

        // Verify correct search flow was used
        verify(userClient).searchUsers(uploaderUsername);
        verify(documentRepository).findByFilenameLikeIgnoreCaseAndUserId(documentTitle, uploaderUserId.toString());
        verify(documentReportRepository).findDocumentReportsGroupedByProcessedAndDocumentIds(
                eq(status.name()), eq(fromDate), eq(toDate), eq(reportTypeCode), anyCollection(), eq(pageable));
    }

    @Test
    void getAdminDocumentReports_WithDocumentTitleOnly_ReturnsFilteredReports() {
        // Arrange
        String documentTitle = "Test Document";
        DocumentReportStatus status = DocumentReportStatus.PENDING;
        Instant fromDate = Instant.now().minus(java.time.Duration.ofDays(7));
        Instant toDate = Instant.now();
        String reportTypeCode = "COPYRIGHT";
        Pageable pageable = mock(Pageable.class);

        // Create expected document information
        DocumentInformation documentInfo = DocumentInformation.builder()
                .id(documentId)
                .filename("Test Document")
                .userId(UUID.randomUUID().toString())
                .build();

        // Mock finding documents by title
        when(documentRepository.findByFilenameLikeIgnoreCase(documentTitle))
                .thenReturn(Collections.singletonList(documentInfo));

        // Mock projection
        DocumentReportProjection projection = mock(DocumentReportProjection.class);
        when(projection.getDocumentId()).thenReturn(documentId);
        when(projection.getStatus()).thenReturn(DocumentReportStatus.PENDING);
        when(projection.getProcessed()).thenReturn(false);
        when(projection.getReportCount()).thenReturn(1);
        when(projection.getUpdatedBy()).thenReturn(null);
        when(projection.getUpdatedAt()).thenReturn(null);

        List<DocumentReportProjection> projections = Collections.singletonList(projection);
        Page<DocumentReportProjection> projectionPage = new PageImpl<>(projections, pageable, 1);

        // Set up a capture for the document IDs collection
        ArgumentCaptor<Collection<String>> docIdsCaptor = ArgumentCaptor.forClass(Collection.class);

        // Mock repository call with filtered document IDs
        when(documentReportRepository.findDocumentReportsGroupedByProcessedAndDocumentIds(
                eq(status.name()),
                eq(fromDate),
                eq(toDate),
                eq(reportTypeCode),
                docIdsCaptor.capture(),
                eq(pageable)))
                .thenReturn(projectionPage);

        // Mock finding document by ID
        when(documentRepository.findAllById(anyList()))
                .thenReturn(Collections.singletonList(documentInfo));

        // Mock username retrieval
        when(userClient.getUsersByIds(anyList())).thenReturn(ResponseEntity.ok(Collections.emptyList()));

        // Act
        Page<AdminDocumentReportResponse> result = documentReportService.getAdminDocumentReports(
                documentTitle, null, fromDate, toDate, status, reportTypeCode, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size()); // Make sure we have one item
        assertEquals(documentId, result.getContent().get(0).documentId());

        // Verify the document IDs passed to the repository method
        Collection<String> capturedDocIds = docIdsCaptor.getValue();
        assertTrue(capturedDocIds.contains(documentId));

        // Verify correct search flow was used
        verify(documentRepository).findByFilenameLikeIgnoreCase(documentTitle);
        verify(documentReportRepository).findDocumentReportsGroupedByProcessedAndDocumentIds(
                eq(status.name()), eq(fromDate), eq(toDate), eq(reportTypeCode), anyCollection(), eq(pageable));
    }

    @Test
    void getDocumentReportDetails_ReturnsReportDetails() {
        // Arrange
        DocumentReportStatus status = DocumentReportStatus.PENDING;
        List<DocumentReport> reports = Collections.singletonList(documentReport);

        when(documentReportRepository.findByDocumentIdAndStatus(documentId, status))
                .thenReturn(reports);

        // Mock username retrieval - ensure it returns a non-null response
        List<UserResponse> userResponseList = Collections.singletonList(userResponse);
        when(userClient.getUsersByIds(anyList())).thenReturn(ResponseEntity.ok(userResponseList));

        // Act
        List<DocumentReportDetail> result = documentReportService.getDocumentReportDetails(documentId, status);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        // In DocumentReportDetail, the document ID is part of the constructor but not exposed as a getter
        // Instead, verify the reporter and report type info
        assertEquals(userId, result.get(0).reporterUserId());
        assertEquals(username, result.get(0).reporterUsername());
        assertEquals("COPYRIGHT", result.get(0).reportTypeCode());
    }

    @Test
    void getUsernameById_UserFound_ReturnsUsername() {
        // Setup reflection to test private method
        UUID testUserId = UUID.randomUUID();
        List<UserResponse> userResponses = Collections.singletonList(
                new UserResponse(testUserId, "founduser", "user@example.com", null)
        );

        when(userClient.getUsersByIds(Collections.singletonList(testUserId)))
                .thenReturn(ResponseEntity.ok(userResponses));

        // Need to test through a public method that calls this private method
        DocumentReport reportWithTestUser = new DocumentReport();
        reportWithTestUser.setUserId(testUserId);
        reportWithTestUser.setDocumentId(documentId);
        reportWithTestUser.setReportTypeCode("COPYRIGHT");

        when(documentReportRepository.findByDocumentIdAndStatus(documentId, DocumentReportStatus.PENDING))
                .thenReturn(Collections.singletonList(reportWithTestUser));

        // Act
        List<DocumentReportDetail> result = documentReportService.getDocumentReportDetails(documentId, DocumentReportStatus.PENDING);

        // Assert - if we get here without errors, the private method worked
        assertNotNull(result);
        assertEquals("founduser", result.get(0).reporterUsername());
    }

    @Test
    void getUsernameById_UserClientError_ReturnsUnknown() {
        // Setup reflection to test private method
        UUID testUserId = UUID.randomUUID();

        // Mock UserClient to throw an exception
        when(userClient.getUsersByIds(Collections.singletonList(testUserId)))
                .thenThrow(new RuntimeException("API error"));

        // Create a test report with our test userId to trigger the error path
        DocumentReport reportWithTestUser = new DocumentReport();
        reportWithTestUser.setUserId(testUserId);
        reportWithTestUser.setDocumentId(documentId);
        reportWithTestUser.setReportTypeCode("COPYRIGHT");

        // Need to test through a public method that calls this private method
        when(documentReportRepository.findByDocumentIdAndStatus(documentId, DocumentReportStatus.PENDING))
                .thenReturn(Collections.singletonList(reportWithTestUser));

        // Act
        List<DocumentReportDetail> result = documentReportService.getDocumentReportDetails(documentId, DocumentReportStatus.PENDING);

        // Assert
        assertNotNull(result);
        assertEquals("Unknown", result.get(0).reporterUsername());
    }

    @Test
    void getUsernameById_EmptyResponse_ReturnsUnknown() {
        // Setup reflection to test private method
        UUID testUserId = UUID.randomUUID();

        // Mock UserClient to return an empty list
        when(userClient.getUsersByIds(Collections.singletonList(testUserId)))
                .thenReturn(ResponseEntity.ok(Collections.emptyList()));

        // Create a test report with our test userId
        DocumentReport reportWithTestUser = new DocumentReport();
        reportWithTestUser.setUserId(testUserId);
        reportWithTestUser.setDocumentId(documentId);
        reportWithTestUser.setReportTypeCode("COPYRIGHT");

        // Need to test through a public method that calls this private method
        when(documentReportRepository.findByDocumentIdAndStatus(documentId, DocumentReportStatus.PENDING))
                .thenReturn(Collections.singletonList(reportWithTestUser));

        // Act
        List<DocumentReportDetail> result = documentReportService.getDocumentReportDetails(documentId, DocumentReportStatus.PENDING);

        // Assert
        assertNotNull(result);
        assertEquals("Unknown", result.get(0).reporterUsername());
    }

    @Test
    void getUsernameById_NullResponse_ReturnsUnknown() {
        // Setup reflection to test private method
        UUID testUserId = UUID.randomUUID();

        // Mock UserClient to return null response
        when(userClient.getUsersByIds(Collections.singletonList(testUserId)))
                .thenReturn(null);

        // Create a test report with our test userId
        DocumentReport reportWithTestUser = new DocumentReport();
        reportWithTestUser.setUserId(testUserId);
        reportWithTestUser.setDocumentId(documentId);
        reportWithTestUser.setReportTypeCode("COPYRIGHT");

        // Need to test through a public method that calls this private method
        when(documentReportRepository.findByDocumentIdAndStatus(documentId, DocumentReportStatus.PENDING))
                .thenReturn(Collections.singletonList(reportWithTestUser));

        // Act
        List<DocumentReportDetail> result = documentReportService.getDocumentReportDetails(documentId, DocumentReportStatus.PENDING);

        // Assert
        assertNotNull(result);
        assertEquals("Unknown", result.get(0).reporterUsername());
    }

    @Test
    void getUserFromUsername_UserNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(null));

        // Act & Assert
        assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                documentReportService.createReport(documentId, new ReportRequest("COPYRIGHT", "Test"), username)
        );
    }
}