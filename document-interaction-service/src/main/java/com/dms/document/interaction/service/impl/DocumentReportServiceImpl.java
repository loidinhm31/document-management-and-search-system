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
import com.dms.document.interaction.model.projection.DocumentReportProjection;
import com.dms.document.interaction.repository.DocumentReportRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.MasterDataRepository;
import com.dms.document.interaction.service.DocumentReportService;
import com.dms.document.interaction.service.PublishEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentReportServiceImpl implements DocumentReportService {
    private final DocumentReportRepository documentReportRepository;
    private final DocumentRepository documentRepository;
    private final MasterDataRepository masterDataRepository;
    private final UserClient userClient;
    private final PublishEventService publishEventService;
    private final ReportTypeMapper reportTypeMapper;

    @Transactional
    @Override
    public ReportResponse createReport(String documentId, ReportRequest request, String username) {
        // Get user information
        UserResponse userResponse = getUserFromUsername(username);

        // Verify document exists
        documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userResponse.userId().toString())
                .orElseThrow(() -> new IllegalArgumentException("Document not found or not accessible"));

        // Verify report type exists in master data
        MasterData reportType = masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_DOCUMENT_TYPE, request.reportTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid report type"));

        // Check if user has already reported this document
        if (documentReportRepository.existsByDocumentIdAndUserIdAndProcessed(documentId, userResponse.userId(), Boolean.FALSE)) {
            throw new IllegalStateException("You have already reported this document");
        }

        // Find user's processed report if it exists
        List<DocumentReport> processedReports = documentReportRepository.findByDocumentIdAndProcessed(documentId, true);
        Optional<DocumentReport> maxTimeProcessedReport = processedReports.stream()
                .max(Comparator.comparing(report -> report.getTimes() != null ? report.getTimes() : 0));

        int times = 1;
        if (maxTimeProcessedReport.isPresent()) {
            // Update existing report by incrementing times
            DocumentReport maxTimeReport = maxTimeProcessedReport.get();
            times = Objects.nonNull(maxTimeReport.getTimes()) ? maxTimeReport.getTimes() + 1 : 1;
        }

        // Create and save report
        DocumentReport report = new DocumentReport();
        report.setDocumentId(documentId);
        report.setUserId(userResponse.userId());
        report.setReportTypeCode(reportType.getCode());
        report.setDescription(request.description());
        report.setStatus(DocumentReportStatus.PENDING);
        report.setProcessed(Boolean.FALSE);
        report.setTimes(times);
        report.setCreatedAt(Instant.now());

        DocumentReport savedReport = documentReportRepository.save(report);
        return reportTypeMapper.mapToResponse(savedReport, reportType);
    }

    @Transactional
    @Override
    public void updateReportStatus(String documentId, DocumentReportStatus newStatus, String username) {
        UserResponse resolver = getUserFromUsername(username);
        if (!resolver.role().roleName().equals(AppRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Only administrators can update report status");
        }

        // Find current status of report
        DocumentReport currentReport = documentReportRepository.findByDocumentIdAndProcessed(documentId, false)
                .stream()
                .max(Comparator.comparing(DocumentReport::getCreatedAt))
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        if (currentReport.getStatus() == DocumentReportStatus.REJECTED || BooleanUtils.isTrue(currentReport.getProcessed())) {
            throw new IllegalStateException("Report has already been processed");
        }

        if (currentReport.getStatus() == newStatus) {
            throw new IllegalStateException("Cannot update the same status");
        }

        // Update status for related reports
        AtomicInteger times = new AtomicInteger();
        Instant updatedAt = Instant.now();
        List<DocumentReport> documentReports = documentReportRepository.findByDocumentIdAndProcessed(documentId, false);
        documentReports.forEach((dr) -> {
            times.set(dr.getTimes());
            dr.setStatus(newStatus);
            dr.setUpdatedBy(resolver.userId());
            dr.setUpdatedAt(updatedAt);

            if (newStatus == DocumentReportStatus.REJECTED || newStatus == DocumentReportStatus.REMEDIATED) {
                dr.setProcessed(Boolean.TRUE);
            }
        });
        documentReportRepository.saveAll(documentReports);

        // Update main document status
        DocumentInformation document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        document.setReportStatus(newStatus);
        documentRepository.save(document);

        // Send sync event
        CompletableFuture.runAsync(() ->
                publishEventService.sendSyncEvent(
                        SyncEventRequest.builder()
                                .eventId(UUID.randomUUID().toString())
                                .userId(resolver.userId().toString())
                                .documentId(documentId)
                                .subject(EventType.DOCUMENT_REPORT_PROCESS_EVENT.name())
                                .triggerAt(Instant.now())
                                .versionNumber(times.get())
                                .build()
                )
        );
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<ReportResponse> getUserReport(String documentId, String username) {
        UserResponse userResponse = getUserFromUsername(username);

        return documentReportRepository.findByDocumentIdAndUserIdAndProcessed(documentId, userResponse.userId(), false)
                .map(report -> {
                    MasterData reportType = masterDataRepository.findByTypeAndCode(
                            MasterDataType.REPORT_DOCUMENT_TYPE,
                            report.getReportTypeCode()
                    ).orElseThrow(() -> new IllegalStateException("Report type not found"));
                    return reportTypeMapper.mapToResponse(report, reportType);
                });
    }

    @Override
    public List<ReportTypeResponse> getReportTypes() {
        return masterDataRepository.findByTypeAndIsActive(MasterDataType.REPORT_DOCUMENT_TYPE, true)
                .stream()
                .map(reportTypeMapper::mapToReportTypeResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public Page<AdminDocumentReportResponse> getAdminDocumentReports(
            String documentTitle,
            String uploaderUsername,
            Instant fromDate,
            Instant toDate,
            DocumentReportStatus status,
            String reportTypeCode,
            Pageable pageable) {

        // Pre-filter document IDs by title and/or uploader username
        Set<String> filteredDocumentIds = null;

        // Get all users matching the username search if provided
        if (StringUtils.isNotEmpty(uploaderUsername)) {
            try {
                ResponseEntity<List<UserResponse>> response = userClient.searchUsers(uploaderUsername);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && !response.getBody().isEmpty()) {
                    // Extract user IDs from the search results
                    List<String> userIds = response.getBody().stream()
                            .map(user -> user.userId().toString())
                            .toList();

                    // Find all documents by these users
                    List<DocumentInformation> userDocuments;
                    if (StringUtils.isNotEmpty(documentTitle)) {
                        // If we also have a title filter, get documents matching both criteria
                        userDocuments = new ArrayList<>();
                        for (String userId : userIds) {
                            userDocuments.addAll(documentRepository.findByFilenameLikeIgnoreCaseAndUserId(documentTitle, userId));
                        }
                    } else {
                        // Otherwise just get all documents from these users
                        userDocuments = new ArrayList<>();
                        for (String userId : userIds) {
                            userDocuments.addAll(documentRepository.findByUserIdAndNotDeleted(userId));
                        }
                    }

                    if (userDocuments.isEmpty()) {
                        return new PageImpl<>(Collections.emptyList(), pageable, 0);
                    }

                    filteredDocumentIds = userDocuments.stream()
                            .map(DocumentInformation::getId)
                            .collect(Collectors.toSet());
                } else {
                    // No users found matching the search criteria
                    return new PageImpl<>(Collections.emptyList(), pageable, 0);
                }
            } catch (Exception e) {
                log.warn("Failed to search for users with username: {}", uploaderUsername, e);
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        } else if (StringUtils.isNotEmpty(documentTitle)) {
            // If we only have a title filter (no username filter)
            List<DocumentInformation> matchingDocuments = documentRepository.findByFilenameLikeIgnoreCase(documentTitle);
            if (matchingDocuments.isEmpty()) {
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
            filteredDocumentIds = matchingDocuments.stream()
                    .map(DocumentInformation::getId)
                    .collect(Collectors.toSet());
        }

        // Get document reports grouped by document_id and processed flag
        String statusStr = status != null ? status.name() : null;
        Page<DocumentReportProjection> reportProjections;

        if (filteredDocumentIds != null && !filteredDocumentIds.isEmpty()) {
            // Filter by document IDs and other criteria
            reportProjections = documentReportRepository.findDocumentReportsGroupedByProcessedAndDocumentIds(
                    statusStr, fromDate, toDate, reportTypeCode, filteredDocumentIds, pageable);
        } else {
            // Use original query without document filtering
            reportProjections = documentReportRepository.findDocumentReportsGroupedByProcessed(
                    statusStr, fromDate, toDate, reportTypeCode, pageable);
        }

        List<DocumentReportProjection> projections = reportProjections.getContent();
        if (projections.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // Extract the document IDs to load the documents
        List<String> documentIds = projections.stream()
                .map(DocumentReportProjection::getDocumentId)
                .distinct()
                .collect(Collectors.toList());

        // Load all documents for these IDs
        Map<String, DocumentInformation> documentMap = documentRepository.findAllById(documentIds).stream()
                .collect(Collectors.toMap(DocumentInformation::getId, Function.identity()));

        // Cache for usernames to avoid repeated API calls
        Map<UUID, String> usernameCache = new HashMap<>();

        // Map projection to response
        List<AdminDocumentReportResponse> responses = projections.stream()
                .map(projection -> {
                    DocumentInformation doc = documentMap.get(projection.getDocumentId());

                    // Skip if document not found
                    if (doc == null) return null;

                    UUID docOwnerId = UUID.fromString(doc.getUserId());

                    // Get document owner username (using cache to avoid duplicate API calls)
                    String ownerUsername = usernameCache.computeIfAbsent(docOwnerId, this::getUsernameById);

                    // Get resolver username if present (also using cache)
                    String resolverUsername = null;
                    if (projection.getUpdatedBy() != null) {
                        resolverUsername = usernameCache.computeIfAbsent(projection.getUpdatedBy(), this::getUsernameById);
                    }

                    return new AdminDocumentReportResponse(
                            projection.getDocumentId(),
                            doc.getFilename(),
                            docOwnerId,
                            ownerUsername,
                            projection.getStatus(),
                            projection.getProcessed() != null ? projection.getProcessed() : false,
                            projection.getReportCount(),
                            projection.getUpdatedBy(),
                            resolverUsername,
                            projection.getUpdatedAt()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, reportProjections.getTotalElements());
    }

    @Transactional(readOnly = true)
    @Override
    public List<DocumentReportDetail> getDocumentReportDetails(String documentId, DocumentReportStatus status) {
        List<DocumentReport> reports = documentReportRepository.findByDocumentIdAndStatus(documentId, status);

        if (reports.isEmpty()) {
            return Collections.emptyList();
        }

        // Sort reports by creation date (newest first)
        reports.sort(Comparator.comparing(DocumentReport::getCreatedAt).reversed());

        return reports.stream()
                .map(this::mapToReportDetail)
                .collect(Collectors.toList());
    }

    private DocumentReportDetail mapToReportDetail(DocumentReport report) {
        return new DocumentReportDetail(
                report.getId(),
                report.getUserId(),
                getUsernameById(report.getUserId()),
                report.getReportTypeCode(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }

    private String getUsernameById(UUID userId) {
        try {
            ResponseEntity<List<UserResponse>> response = userClient.getUsersByIds(List.of(userId));
            List<UserResponse> users = response.getBody();
            return users != null && !users.isEmpty() ? users.get(0).username() : "Unknown";
        } catch (Exception e) {
            log.warn("Failed to fetch username for user ID: {}", userId, e);
            return "Unknown";
        }
    }

    private UserResponse getUserFromUsername(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        return response.getBody();
    }
}