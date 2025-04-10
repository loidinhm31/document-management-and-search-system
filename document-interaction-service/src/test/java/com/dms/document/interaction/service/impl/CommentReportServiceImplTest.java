package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.CommentReportStatus;
import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.mapper.ReportTypeMapper;
import com.dms.document.interaction.model.CommentReport;
import com.dms.document.interaction.model.DocumentComment;
import com.dms.document.interaction.model.MasterData;
import com.dms.document.interaction.model.Translation;
import com.dms.document.interaction.model.projection.CommentReportProjection;
import com.dms.document.interaction.repository.CommentReportRepository;
import com.dms.document.interaction.repository.DocumentCommentRepository;
import com.dms.document.interaction.repository.MasterDataRepository;
import com.dms.document.interaction.service.DocumentNotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.apache.commons.lang3.BooleanUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommentReportServiceImplTest {

    @Mock
    private CommentReportRepository commentReportRepository;

    @Mock
    private DocumentCommentRepository documentCommentRepository;

    @Mock
    private MasterDataRepository masterDataRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private ReportTypeMapper reportTypeMapper;

    @Mock
    private DocumentNotificationService documentNotificationService;

    @InjectMocks
    private CommentReportServiceImpl commentReportService;

    private final UUID userId = UUID.randomUUID();
    private final String documentId = "doc123";
    private final Long commentId = 1L;
    private final String username = "testuser";
    private UserResponse userResponse;
    private UserResponse adminResponse;
    private MasterData reportType;
    private DocumentComment comment;
    private CommentReport report;
    private CommentReportResponse reportResponse;

    @BeforeEach
    void setUp() {
        // Setup user response
        RoleResponse roleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER);
        userResponse = new UserResponse(userId, username, "test@example.com", roleResponse);

        // Setup admin response
        RoleResponse adminRoleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        adminResponse = new UserResponse(UUID.randomUUID(), "admin", "admin@example.com", adminRoleResponse);

        // Setup report type
        reportType = new MasterData();
        reportType.setId("rt1");
        reportType.setCode("SPAM");
        reportType.setType(MasterDataType.REPORT_COMMENT_TYPE);
        Translation translation = new Translation();
        translation.setEn("Spam");
        translation.setVi("Thư rác");
        reportType.setTranslations(translation);

        // Setup comment
        comment = new DocumentComment();
        comment.setId(commentId);
        comment.setDocumentId(documentId);
        comment.setUserId(UUID.randomUUID());
        comment.setContent("Test comment");
        comment.setCreatedAt(Instant.now());

        // Setup report
        report = new CommentReport();
        report.setId(1L);
        report.setDocumentId(documentId);
        report.setCommentId(commentId);
        report.setUserId(userId);
        report.setReportTypeCode("SPAM");
        report.setDescription("This is spam");
        report.setStatus(CommentReportStatus.PENDING);
        report.setProcessed(false);
        report.setCreatedAt(Instant.now());
        report.setComment(comment);
        report.setTimes(1); // Set times to avoid NullPointerException

        // Setup report response
        TranslationDTO translationDTO = new TranslationDTO();
        translationDTO.setEn("Spam");
        translationDTO.setVi("Thư rác");
        reportResponse = new CommentReportResponse(
                1L, documentId, commentId, "SPAM", translationDTO, "This is spam", Instant.now()
        );
    }

    @Test
    void createReport_Success() {
        // Arrange
        CommentReportRequest request = new CommentReportRequest("SPAM", "This is spam");

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentCommentRepository.findByDocumentIdAndId(documentId, commentId)).thenReturn(Optional.of(comment));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_COMMENT_TYPE, "SPAM")).thenReturn(Optional.of(reportType));
        when(commentReportRepository.existsByCommentIdAndUserIdAndProcessed(commentId, userId, Boolean.FALSE)).thenReturn(false);
        when(commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.TRUE)).thenReturn(Collections.emptyList());
        when(commentReportRepository.save(any(CommentReport.class))).thenReturn(report);
        when(reportTypeMapper.mapToResponse(any(CommentReport.class), any(MasterData.class))).thenReturn(reportResponse);

        // Act
        CommentReportResponse result = commentReportService.createReport(documentId, commentId, request, username);

        // Assert
        assertNotNull(result);
        assertEquals(documentId, result.documentId());
        assertEquals(commentId, result.commentId());
        assertEquals("SPAM", result.reportTypeCode());

        // Verify the report was saved with correct details
        ArgumentCaptor<CommentReport> reportCaptor = ArgumentCaptor.forClass(CommentReport.class);
        verify(commentReportRepository).save(reportCaptor.capture());
        CommentReport savedReport = reportCaptor.getValue();
        assertEquals(documentId, savedReport.getDocumentId());
        assertEquals(commentId, savedReport.getCommentId());
        assertEquals(userId, savedReport.getUserId());
        assertEquals("SPAM", savedReport.getReportTypeCode());
        assertEquals("This is spam", savedReport.getDescription());
        assertEquals(CommentReportStatus.PENDING, savedReport.getStatus());
        assertFalse(savedReport.getProcessed());
    }

    @Test
    void createReport_AdminUser_ThrowsException() {
        // Arrange
        CommentReportRequest request = new CommentReportRequest("SPAM", "This is spam");

        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

        // Act & Assert
        InvalidDataAccessResourceUsageException exception = assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                commentReportService.createReport(documentId, commentId, request, "admin")
        );
        assertEquals("You are not allowed to create a comment report", exception.getMessage());

        // Verify no further processing took place
        verify(documentCommentRepository, never()).findByDocumentIdAndId(anyString(), anyLong());
        verify(commentReportRepository, never()).save(any(CommentReport.class));
    }

    @Test
    void createReport_UserAlreadyReported_ThrowsException() {
        // Arrange
        CommentReportRequest request = new CommentReportRequest("SPAM", "This is spam");

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentCommentRepository.findByDocumentIdAndId(documentId, commentId)).thenReturn(Optional.of(comment));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_COMMENT_TYPE, "SPAM")).thenReturn(Optional.of(reportType));
        when(commentReportRepository.existsByCommentIdAndUserIdAndProcessed(commentId, userId, Boolean.FALSE)).thenReturn(true);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                commentReportService.createReport(documentId, commentId, request, username)
        );
        assertEquals("You have already reported this comment", exception.getMessage());
    }

    @Test
    void createReport_CommentNotFound_ThrowsException() {
        // Arrange
        CommentReportRequest request = new CommentReportRequest("SPAM", "This is spam");

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentCommentRepository.findByDocumentIdAndId(documentId, commentId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                commentReportService.createReport(documentId, commentId, request, username)
        );
        assertEquals("Comment not found", exception.getMessage());
    }

    @Test
    void createReport_IncrementExistingReportTimes() {
        // Arrange
        CommentReportRequest request = new CommentReportRequest("SPAM", "This is spam");

        // Create a previously processed report with times=2
        CommentReport processedReport = new CommentReport();
        processedReport.setTimes(2);

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentCommentRepository.findByDocumentIdAndId(documentId, commentId)).thenReturn(Optional.of(comment));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_COMMENT_TYPE, "SPAM")).thenReturn(Optional.of(reportType));
        when(commentReportRepository.existsByCommentIdAndUserIdAndProcessed(commentId, userId, Boolean.FALSE)).thenReturn(false);
        when(commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.TRUE)).thenReturn(List.of(processedReport));
        when(commentReportRepository.save(any(CommentReport.class))).thenReturn(report);
        when(reportTypeMapper.mapToResponse(any(CommentReport.class), any(MasterData.class))).thenReturn(reportResponse);

        // Act
        commentReportService.createReport(documentId, commentId, request, username);

        // Assert
        ArgumentCaptor<CommentReport> reportCaptor = ArgumentCaptor.forClass(CommentReport.class);
        verify(commentReportRepository).save(reportCaptor.capture());
        CommentReport savedReport = reportCaptor.getValue();

        // Verify times was incremented from previous report
        assertEquals(3, savedReport.getTimes());
    }

    @Test
    void createReport_InvalidReportType_ThrowsException() {
        // Arrange
        CommentReportRequest request = new CommentReportRequest("INVALID", "This is spam");

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentCommentRepository.findByDocumentIdAndId(documentId, commentId)).thenReturn(Optional.of(comment));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_COMMENT_TYPE, "INVALID")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                commentReportService.createReport(documentId, commentId, request, username)
        );
        assertEquals("Invalid report type", exception.getMessage());
    }

    @Test
    void getUserReport_Success() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(commentReportRepository.findByDocumentIdAndCommentIdAndUserIdAndProcessed(documentId, commentId, userId, Boolean.FALSE))
                .thenReturn(Optional.of(report));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_COMMENT_TYPE, "SPAM"))
                .thenReturn(Optional.of(reportType));
        when(reportTypeMapper.mapToResponse(report, reportType)).thenReturn(reportResponse);

        // Act
        Optional<CommentReportResponse> result = commentReportService.getUserReport(documentId, commentId, username);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(documentId, result.get().documentId());
        assertEquals(commentId, result.get().commentId());
        assertEquals("SPAM", result.get().reportTypeCode());
    }

    @Test
    void getUserReport_NotFound_ReturnsEmpty() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(commentReportRepository.findByDocumentIdAndCommentIdAndUserIdAndProcessed(documentId, commentId, userId, Boolean.FALSE))
                .thenReturn(Optional.empty());

        // Act
        Optional<CommentReportResponse> result = commentReportService.getUserReport(documentId, commentId, username);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getReportTypes_Success() {
        // Arrange
        List<MasterData> reportTypes = new ArrayList<>();
        reportTypes.add(reportType);

        when(masterDataRepository.findByTypeAndIsActive(MasterDataType.REPORT_COMMENT_TYPE, true)).thenReturn(reportTypes);
        when(reportTypeMapper.mapToReportTypeResponse(reportType)).thenReturn(new ReportTypeResponse("SPAM", new TranslationDTO(), ""));

        // Act
        List<ReportTypeResponse> result = commentReportService.getReportTypes();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("SPAM", result.get(0).code());
    }

    @Test
    void resolveCommentReport_Success() {
        // Arrange
        // Setup admin user
        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

        // Setup a list of reports to be resolved
        List<CommentReport> commentReports = new ArrayList<>();
        CommentReport pendingReport = new CommentReport();
        pendingReport.setId(1L);
        pendingReport.setDocumentId(documentId);
        pendingReport.setCommentId(commentId);
        pendingReport.setUserId(userId);
        pendingReport.setReportTypeCode("SPAM");
        pendingReport.setDescription("This is spam");
        pendingReport.setStatus(CommentReportStatus.PENDING);
        pendingReport.setProcessed(false);
        pendingReport.setCreatedAt(Instant.now());
        pendingReport.setTimes(1);
        commentReports.add(pendingReport);

        when(commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.FALSE)).thenReturn(commentReports);

        // Mock finding comment
        when(documentCommentRepository.findByDocumentIdAndId(documentId, commentId)).thenReturn(Optional.of(comment));

        // Setup saveAll
        when(commentReportRepository.saveAll(anyList())).thenReturn(commentReports);

        // Setup document comment save
        when(documentCommentRepository.save(any(DocumentComment.class))).thenReturn(comment);

        // Act
        commentReportService.resolveCommentReport(commentId, CommentReportStatus.RESOLVED, "admin");

        // Assert
        // Verify reports were updated correctly
        verify(commentReportRepository).saveAll(argThat(reports -> {
            CommentReport report = ((List<CommentReport>)reports).get(0);
            return report.getStatus() == CommentReportStatus.RESOLVED &&
                   report.getProcessed() == Boolean.TRUE &&
                   report.getUpdatedBy() != null &&
                   report.getUpdatedAt() != null;
        }));

        // Verify comment was updated for resolution
        verify(documentCommentRepository).save(argThat(dc ->
                dc.getContent().equals("[deleted]") &&
                dc.getFlag() == -1
        ));

        // Verify notification was triggered (via CompletableFuture)
        verify(documentNotificationService, timeout(1000)).sendCommentReportResolvedNotification(
                eq(documentId), eq(commentId), any(UUID.class), eq(1)
        );
    }

    @Test
    void resolveCommentReport_RejectStatus() {
        // Arrange
        // Setup admin user
        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

        // Setup a list of reports to be resolved
        List<CommentReport> commentReports = new ArrayList<>();
        CommentReport pendingReport = new CommentReport();
        pendingReport.setId(1L);
        pendingReport.setDocumentId(documentId);
        pendingReport.setCommentId(commentId);
        pendingReport.setUserId(userId);
        pendingReport.setReportTypeCode("SPAM");
        pendingReport.setDescription("This is spam");
        pendingReport.setStatus(CommentReportStatus.PENDING);
        pendingReport.setProcessed(false);
        pendingReport.setCreatedAt(Instant.now());
        pendingReport.setTimes(1);
        commentReports.add(pendingReport);

        when(commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.FALSE)).thenReturn(commentReports);

        // Mock finding comment
        when(documentCommentRepository.findByDocumentIdAndId(documentId, commentId)).thenReturn(Optional.of(comment));

        // Setup saveAll
        when(commentReportRepository.saveAll(anyList())).thenReturn(commentReports);

        // Setup document comment save
        when(documentCommentRepository.save(any(DocumentComment.class))).thenReturn(comment);

        // Act
        commentReportService.resolveCommentReport(commentId, CommentReportStatus.REJECTED, "admin");

        // Assert
        // Verify reports were updated correctly
        verify(commentReportRepository).saveAll(argThat(reports -> {
            CommentReport report = ((List<CommentReport>)reports).get(0);
            return report.getStatus() == CommentReportStatus.REJECTED &&
                   report.getProcessed() == Boolean.TRUE &&
                   report.getUpdatedBy() != null &&
                   report.getUpdatedAt() != null;
        }));

        // Verify comment was updated for rejection (flag = 1)
        verify(documentCommentRepository).save(argThat(dc ->
                dc.getFlag() == 1
        ));

        // Verify notification was triggered
        verify(documentNotificationService, timeout(1000)).sendCommentReportResolvedNotification(
                eq(documentId), eq(commentId), any(UUID.class), eq(1)
        );
    }

    @Test
    void resolveCommentReport_NonAdmin_ThrowsException() {
        // Arrange
        String nonAdminUsername = "user";

        when(userClient.getUserByUsername(nonAdminUsername)).thenReturn(ResponseEntity.ok(userResponse)); // Non-admin user

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                commentReportService.resolveCommentReport(commentId, CommentReportStatus.RESOLVED, nonAdminUsername)
        );
        assertEquals("Only administrators can resolve reports", exception.getMessage());
    }

    @Test
    void resolveCommentReport_EmptyReports_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));
        when(commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.FALSE)).thenReturn(Collections.emptyList());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                commentReportService.resolveCommentReport(commentId, CommentReportStatus.RESOLVED, "admin")
        );
        assertEquals("Comment report not found", exception.getMessage());
    }

    @Test
    void resolveCommentReport_AlreadyProcessed_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

        // Create a report that's already processed
        CommentReport processedReport = new CommentReport();
        processedReport.setProcessed(true);
        processedReport.setStatus(CommentReportStatus.REJECTED);
        processedReport.setCreatedAt(Instant.now());

        when(commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.FALSE))
                .thenReturn(List.of(processedReport));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                commentReportService.resolveCommentReport(commentId, CommentReportStatus.RESOLVED, "admin")
        );
        assertEquals("Report has already been processed", exception.getMessage());
    }

    @Test
    void resolveCommentReport_SameStatus_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));

        // Create a report with PENDING status
        CommentReport pendingReport = new CommentReport();
        pendingReport.setProcessed(false);
        pendingReport.setStatus(CommentReportStatus.PENDING);
        pendingReport.setCreatedAt(Instant.now());

        when(commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.FALSE))
                .thenReturn(List.of(pendingReport));

        // Act & Assert - Try to update to same status
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                commentReportService.resolveCommentReport(commentId, CommentReportStatus.PENDING, "admin")
        );
        assertEquals("Cannot update the same status", exception.getMessage());
    }

    @Test
    void getAdminCommentReports_Success() {
        // Arrange
        Instant fromDate = Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant toDate = Instant.now();
        String commentContent = "test";
        String reportTypeCode = "SPAM";
        CommentReportStatus status = CommentReportStatus.PENDING;
        Pageable pageable = Pageable.unpaged();

        // Mock CommentReportProjection
        CommentReportProjection projection = mock(CommentReportProjection.class);
        when(projection.getCommentId()).thenReturn(commentId);
        when(projection.getDocumentId()).thenReturn(documentId);
        when(projection.getCommentContent()).thenReturn("Test comment");
        when(projection.getProcessed()).thenReturn(false);
        when(projection.getStatus()).thenReturn(CommentReportStatus.PENDING);
        when(projection.getReportCount()).thenReturn(1);
        when(projection.getReporterId()).thenReturn(userId);
        when(projection.getCommentUserId()).thenReturn(UUID.randomUUID());
        when(projection.getUpdatedBy()).thenReturn(null);
        when(projection.getReportTypeCode()).thenReturn("SPAM");
        when(projection.getDescription()).thenReturn("This is spam");
        when(projection.getCreatedAt()).thenReturn(Instant.now());
        when(projection.getUpdatedAt()).thenReturn(null);

        List<CommentReportProjection> projections = List.of(projection);
        Page<CommentReportProjection> projectionPage = new PageImpl<>(projections, pageable, projections.size());

        when(commentReportRepository.findCommentReportsGroupedByProcessed(
                fromDate, toDate, commentContent, reportTypeCode, status != null ? status.name() : null, pageable
        )).thenReturn(projectionPage);

        // Mock user lookup for batch fetching
        List<UserResponse> users = new ArrayList<>();
        users.add(userResponse);
        when(userClient.getUsersByIds(anyList())).thenReturn(ResponseEntity.ok(users));

        // Act
        Page<AdminCommentReportResponse> result = commentReportService.getAdminCommentReports(
                fromDate, toDate, commentContent, reportTypeCode, status, pageable
        );

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        AdminCommentReportResponse response = result.getContent().get(0);
        assertEquals(documentId, response.documentId());
        assertEquals(commentId, response.commentId());
        assertEquals("Test comment", response.commentContent());
        assertEquals(userId, response.reporterUserId());
        assertEquals(username, response.reporterUsername());  // Should match from batch fetching
    }

    @Test
    void getAdminCommentReports_WithUpdatedBy() {
        // Arrange
        Pageable pageable = Pageable.unpaged();
        UUID resolverUserId = UUID.randomUUID();
        String resolverUsername = "resolver";

        // Create resolver user response
        RoleResponse resolverRoleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        UserResponse resolverResponse = new UserResponse(resolverUserId, resolverUsername, "resolver@example.com", resolverRoleResponse);

        // Mock CommentReportProjection
        CommentReportProjection projection = mock(CommentReportProjection.class);
        when(projection.getCommentId()).thenReturn(commentId);
        when(projection.getDocumentId()).thenReturn(documentId);
        when(projection.getCommentContent()).thenReturn("Test comment");
        when(projection.getProcessed()).thenReturn(true);
        when(projection.getStatus()).thenReturn(CommentReportStatus.RESOLVED);
        when(projection.getReportCount()).thenReturn(1);
        when(projection.getReporterId()).thenReturn(userId);
        when(projection.getCommentUserId()).thenReturn(UUID.randomUUID());
        when(projection.getUpdatedBy()).thenReturn(resolverUserId);
        when(projection.getReportTypeCode()).thenReturn("SPAM");
        when(projection.getDescription()).thenReturn("This is spam");
        when(projection.getCreatedAt()).thenReturn(Instant.now());
        when(projection.getUpdatedAt()).thenReturn(Instant.now());

        List<CommentReportProjection> projections = List.of(projection);
        Page<CommentReportProjection> projectionPage = new PageImpl<>(projections, pageable, projections.size());

        when(commentReportRepository.findCommentReportsGroupedByProcessed(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(projectionPage);

        // Mock user lookup for batch fetching - return both reporter and resolver
        when(userClient.getUsersByIds(anyList())).thenReturn(
                ResponseEntity.ok(List.of(userResponse, resolverResponse))
        );

        // Act
        Page<AdminCommentReportResponse> result = commentReportService.getAdminCommentReports(
                null, null, null, null, null, pageable
        );

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        AdminCommentReportResponse response = result.getContent().get(0);
        assertEquals(resolverUserId, response.resolvedBy());
        assertEquals(resolverUsername, response.resolvedByUsername());
    }

    @Test
    void getAdminCommentReports_FailedUserLookup() {
        // Arrange
        Pageable pageable = Pageable.unpaged();

        // Mock CommentReportProjection
        CommentReportProjection projection = mock(CommentReportProjection.class);
        when(projection.getCommentId()).thenReturn(commentId);
        when(projection.getDocumentId()).thenReturn(documentId);
        when(projection.getCommentContent()).thenReturn("Test comment");
        when(projection.getProcessed()).thenReturn(false);
        when(projection.getStatus()).thenReturn(CommentReportStatus.PENDING);
        when(projection.getReportCount()).thenReturn(1);
        when(projection.getReporterId()).thenReturn(userId);
        when(projection.getCommentUserId()).thenReturn(UUID.randomUUID());
        when(projection.getUpdatedBy()).thenReturn(null);
        when(projection.getReportTypeCode()).thenReturn("SPAM");
        when(projection.getDescription()).thenReturn("This is spam");
        when(projection.getCreatedAt()).thenReturn(Instant.now());
        when(projection.getUpdatedAt()).thenReturn(null);

        List<CommentReportProjection> projections = List.of(projection);
        Page<CommentReportProjection> projectionPage = new PageImpl<>(projections, pageable, projections.size());

        when(commentReportRepository.findCommentReportsGroupedByProcessed(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(projectionPage);

        // Mock API error
        when(userClient.getUsersByIds(anyList())).thenThrow(new RuntimeException("API failure"));

        // Act
        Page<AdminCommentReportResponse> result = commentReportService.getAdminCommentReports(
                null, null, null, null, null, pageable
        );

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        AdminCommentReportResponse response = result.getContent().get(0);
        assertEquals("Unknown", response.reporterUsername());
        assertEquals("Unknown", response.commentUsername());
    }

    @Test
    void getCommentReportsByCommentId_Success() {
        // Arrange
        CommentReportStatus status = CommentReportStatus.PENDING;

        when(documentCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentReportRepository.findByCommentIdAndStatus(commentId, status)).thenReturn(List.of(report));

        // Mock user lookup for batch fetching
        List<UserResponse> users = new ArrayList<>();
        users.add(userResponse);
        when(userClient.getUsersByIds(anyList())).thenReturn(ResponseEntity.ok(users));

        // Act
        List<CommentReportDetailResponse> result = commentReportService.getCommentReportsByCommentId(commentId, status);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        CommentReportDetailResponse response = result.get(0);
        assertEquals(documentId, response.getDocumentId());
        assertEquals(commentId, response.getCommentId());
        assertEquals("Test comment", response.getCommentContent());
        assertEquals(userId, response.getReporterUserId());
        assertEquals(username, response.getReporterUsername());
    }

    @Test
    void getCommentReportsByCommentId_WithResolverInfo() {
        // Arrange
        CommentReportStatus status = CommentReportStatus.RESOLVED;
        UUID resolverUserId = UUID.randomUUID();
        String resolverUsername = "resolver";

        // Create resolver user response
        RoleResponse resolverRoleResponse = new RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN);
        UserResponse resolverResponse = new UserResponse(resolverUserId, resolverUsername, "resolver@example.com", resolverRoleResponse);

        // Create a resolved report
        CommentReport resolvedReport = new CommentReport();
        resolvedReport.setId(1L);
        resolvedReport.setDocumentId(documentId);
        resolvedReport.setCommentId(commentId);
        resolvedReport.setUserId(userId);
        resolvedReport.setUpdatedBy(resolverUserId);
        resolvedReport.setUpdatedAt(Instant.now());
        resolvedReport.setReportTypeCode("SPAM");
        resolvedReport.setDescription("This is spam");
        resolvedReport.setStatus(CommentReportStatus.RESOLVED);
        resolvedReport.setProcessed(true);
        resolvedReport.setCreatedAt(Instant.now());
        resolvedReport.setComment(comment);

        when(documentCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentReportRepository.findByCommentIdAndStatus(commentId, status)).thenReturn(List.of(resolvedReport));

        // Mock user lookup to include both reporter and resolver
        when(userClient.getUsersByIds(anyList())).thenReturn(ResponseEntity.ok(List.of(userResponse, resolverResponse)));

        // Act
        List<CommentReportDetailResponse> result = commentReportService.getCommentReportsByCommentId(commentId, status);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        CommentReportDetailResponse response = result.get(0);
        assertEquals(resolverUserId, response.getResolvedBy());
        assertEquals(resolverUsername, response.getResolvedByUsername());
    }

    @Test
    void getCommentReportsByCommentId_CommentNotFound() {
        // Arrange
        CommentReportStatus status = CommentReportStatus.PENDING;

        when(documentCommentRepository.findById(commentId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                commentReportService.getCommentReportsByCommentId(commentId, status)
        );
        assertEquals("Comment not found", exception.getMessage());
    }

    @Test
    void getCommentReportsByCommentId_UserLookupError() {
        // Arrange
        CommentReportStatus status = CommentReportStatus.PENDING;

        when(documentCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentReportRepository.findByCommentIdAndStatus(commentId, status)).thenReturn(List.of(report));

        // Mock user lookup failure
        when(userClient.getUsersByIds(anyList())).thenThrow(new RuntimeException("API error"));

        // Act
        List<CommentReportDetailResponse> result = commentReportService.getCommentReportsByCommentId(commentId, status);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        CommentReportDetailResponse response = result.get(0);
        assertEquals("Unknown", response.getReporterUsername());
        assertEquals("Unknown", response.getCommentUsername());
    }

    @Test
    void getUserFromUsername_UserNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(null));

        // Act & Assert
        InvalidDataAccessResourceUsageException exception = assertThrows(InvalidDataAccessResourceUsageException.class, () ->
                commentReportService.createReport(documentId, commentId, new CommentReportRequest("SPAM", "This is spam"), username)
        );
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void createReport_WithNullTimes_HandlesDefaultValue() {
        // Arrange
        CommentReportRequest request = new CommentReportRequest("SPAM", "This is spam");
        CommentReport processedReport = new CommentReport();
        processedReport.setTimes(null);
        processedReport.setProcessed(true);

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentCommentRepository.findByDocumentIdAndId(documentId, commentId)).thenReturn(Optional.of(comment));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_COMMENT_TYPE, "SPAM")).thenReturn(Optional.of(reportType));
        when(commentReportRepository.existsByCommentIdAndUserIdAndProcessed(commentId, userId, Boolean.FALSE)).thenReturn(false);
        when(commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.TRUE)).thenReturn(List.of(processedReport));
        when(commentReportRepository.save(any(CommentReport.class))).thenReturn(report);
        when(reportTypeMapper.mapToResponse(any(CommentReport.class), any(MasterData.class))).thenReturn(reportResponse);

        // Act
        commentReportService.createReport(documentId, commentId, request, username);

        // Assert
        ArgumentCaptor<CommentReport> reportCaptor = ArgumentCaptor.forClass(CommentReport.class);
        verify(commentReportRepository).save(reportCaptor.capture());
        assertEquals(1, reportCaptor.getValue().getTimes());
    }

    @Test
    void createReport_WithNoProcessedReports() {
        // Arrange
        CommentReportRequest request = new CommentReportRequest("SPAM", "This is spam");

        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(documentCommentRepository.findByDocumentIdAndId(documentId, commentId)).thenReturn(Optional.of(comment));
        when(masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_COMMENT_TYPE, "SPAM")).thenReturn(Optional.of(reportType));
        when(commentReportRepository.existsByCommentIdAndUserIdAndProcessed(commentId, userId, Boolean.FALSE)).thenReturn(false);
        when(commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.TRUE)).thenReturn(Collections.emptyList());
        when(commentReportRepository.save(any(CommentReport.class))).thenReturn(report);
        when(reportTypeMapper.mapToResponse(any(CommentReport.class), any(MasterData.class))).thenReturn(reportResponse);

        // Act
        commentReportService.createReport(documentId, commentId, request, username);

        // Assert
        ArgumentCaptor<CommentReport> reportCaptor = ArgumentCaptor.forClass(CommentReport.class);
        verify(commentReportRepository).save(reportCaptor.capture());
        assertEquals(1, reportCaptor.getValue().getTimes());
    }

    @Test
    void resolveCommentReport_CommentNotFound_HandlesGracefully() {
        // Arrange
        when(userClient.getUserByUsername("admin")).thenReturn(ResponseEntity.ok(adminResponse));
        when(commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.FALSE))
                .thenReturn(List.of(report));
        when(documentCommentRepository.findByDocumentIdAndId(any(), any())).thenReturn(Optional.empty());

        // Act
        commentReportService.resolveCommentReport(commentId, CommentReportStatus.RESOLVED, "admin");

        // Assert
        verify(commentReportRepository).saveAll(anyList());
        verify(documentCommentRepository, never()).save(any());
    }

    @Test
    void getUserReport_ReportTypeNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(username)).thenReturn(ResponseEntity.ok(userResponse));
        when(commentReportRepository.findByDocumentIdAndCommentIdAndUserIdAndProcessed(
                documentId, commentId, userId, Boolean.FALSE))
                .thenReturn(Optional.of(report));
        when(masterDataRepository.findByTypeAndCode(
                MasterDataType.REPORT_COMMENT_TYPE, report.getReportTypeCode()))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                commentReportService.getUserReport(documentId, commentId, username));
        assertEquals("Report type not found", exception.getMessage());
    }

    @Test
    void getCommentReportsByCommentId_EmptyReports_ReturnsEmptyList() {
        // Arrange
        when(documentCommentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentReportRepository.findByCommentIdAndStatus(commentId, CommentReportStatus.PENDING))
                .thenReturn(Collections.emptyList());

        // Act
        List<CommentReportDetailResponse> result = commentReportService.getCommentReportsByCommentId(
                commentId, CommentReportStatus.PENDING);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getAdminCommentReports_EmptyProjections_ReturnsEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(commentReportRepository.findCommentReportsGroupedByProcessed(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act
        Page<AdminCommentReportResponse> result = commentReportService.getAdminCommentReports(
                null, null, null, null, null, pageable);

        // Assert
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getAdminCommentReports_UserNotFoundInBatch() {
        // Arrange
        Pageable pageable = Pageable.unpaged();
        UUID differentUserId = UUID.randomUUID();

        // Mock CommentReportProjection with a user ID that won't be found
        CommentReportProjection projection = mock(CommentReportProjection.class);
        when(projection.getCommentId()).thenReturn(commentId);
        when(projection.getDocumentId()).thenReturn(documentId);
        when(projection.getCommentContent()).thenReturn("Test comment");
        when(projection.getProcessed()).thenReturn(false);
        when(projection.getStatus()).thenReturn(CommentReportStatus.PENDING);
        when(projection.getReportCount()).thenReturn(1);
        when(projection.getReporterId()).thenReturn(differentUserId); // Different from what will be returned
        when(projection.getCommentUserId()).thenReturn(differentUserId);
        when(projection.getUpdatedBy()).thenReturn(null);
        when(projection.getReportTypeCode()).thenReturn("SPAM");
        when(projection.getDescription()).thenReturn("This is spam");
        when(projection.getCreatedAt()).thenReturn(Instant.now());
        when(projection.getUpdatedAt()).thenReturn(null);

        List<CommentReportProjection> projections = List.of(projection);
        Page<CommentReportProjection> projectionPage = new PageImpl<>(projections, pageable, projections.size());

        when(commentReportRepository.findCommentReportsGroupedByProcessed(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(projectionPage);

        // Mock empty list response (no matching users)
        when(userClient.getUsersByIds(anyList())).thenReturn(ResponseEntity.ok(List.of()));

        // Act
        Page<AdminCommentReportResponse> result = commentReportService.getAdminCommentReports(
                null, null, null, null, null, pageable
        );

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        AdminCommentReportResponse response = result.getContent().get(0);
        assertEquals("Unknown", response.reporterUsername());
        assertEquals("Unknown", response.commentUsername());
    }

    @Test
    void getAdminCommentReports_NullBodyInUserResponse() {
        // Arrange
        Pageable pageable = Pageable.unpaged();

        // Mock CommentReportProjection
        CommentReportProjection projection = mock(CommentReportProjection.class);
        when(projection.getCommentId()).thenReturn(commentId);
        when(projection.getDocumentId()).thenReturn(documentId);
        when(projection.getCommentContent()).thenReturn("Test comment");
        when(projection.getProcessed()).thenReturn(false);
        when(projection.getStatus()).thenReturn(CommentReportStatus.PENDING);
        when(projection.getReportCount()).thenReturn(1);
        when(projection.getReporterId()).thenReturn(userId);
        when(projection.getCommentUserId()).thenReturn(UUID.randomUUID());
        when(projection.getReportTypeCode()).thenReturn("SPAM");
        when(projection.getDescription()).thenReturn("This is spam");
        when(projection.getCreatedAt()).thenReturn(Instant.now());

        List<CommentReportProjection> projections = List.of(projection);
        Page<CommentReportProjection> projectionPage = new PageImpl<>(projections, pageable, projections.size());

        when(commentReportRepository.findCommentReportsGroupedByProcessed(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(projectionPage);

        // Mock null body in response
        when(userClient.getUsersByIds(anyList())).thenReturn(ResponseEntity.ok(null));

        // Act
        Page<AdminCommentReportResponse> result = commentReportService.getAdminCommentReports(
                null, null, null, null, null, pageable
        );

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        AdminCommentReportResponse response = result.getContent().get(0);
        assertEquals("Unknown", response.reporterUsername());
        assertEquals("Unknown", response.commentUsername());
    }
}