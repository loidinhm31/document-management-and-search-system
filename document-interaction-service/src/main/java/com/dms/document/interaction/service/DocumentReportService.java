package com.dms.document.interaction.service;

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
public class DocumentReportService {
    private final DocumentReportRepository documentReportRepository;
    private final DocumentRepository documentRepository;
    private final MasterDataRepository masterDataRepository;
    private final UserClient userClient;
    private final PublishEventService publishEventService;
    private final ReportTypeMapper reportTypeMapper;

    @Transactional
    public ReportResponse createReport(String documentId, ReportRequest request, String username) {
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
        List<DocumentReport> processedReports = documentReportRepository.findByDocumentIdAndProcessed(documentId, Boolean.TRUE);
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

        // Update main document status
        DocumentInformation document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        document.setDocumentReportStatus(newStatus);
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
    public Optional<ReportResponse> getUserReport(String documentId, String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        return documentReportRepository.findByDocumentIdAndUserIdAndProcessed(documentId, userResponse.userId(), false)
                .map(report -> {
                    MasterData reportType = masterDataRepository.findByTypeAndCode(
                            MasterDataType.REPORT_DOCUMENT_TYPE,
                            report.getReportTypeCode()
                    ).orElseThrow(() -> new IllegalStateException("Report type not found"));
                    return reportTypeMapper.mapToResponse(report, reportType);
                });
    }

    public List<ReportTypeResponse> getReportTypes() {
        return masterDataRepository.findByTypeAndIsActive(MasterDataType.REPORT_DOCUMENT_TYPE, true)
                .stream()
                .map(reportTypeMapper::mapToReportTypeResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AdminDocumentReportResponse> getAdminDocumentReports(
            String documentTitle,
            Instant fromDate,
            Instant toDate,
            DocumentReportStatus status,
            String reportTypeCode,
            Pageable pageable) {

        // Get document reports grouped by document_id and processed flag
        String statusStr = status != null ? status.name() : null;
        Page<DocumentReportProjection> reportProjections = documentReportRepository.findDocumentReportsGroupedByProcessed(
                statusStr, fromDate, toDate, reportTypeCode, pageable);

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

        // Filter by document title if specified
        if (StringUtils.isNotEmpty(documentTitle)) {
            Set<String> filteredDocIds = documentMap.values().stream()
                    .filter(doc -> StringUtils.containsIgnoreCase(doc.getFilename(), documentTitle))
                    .map(DocumentInformation::getId)
                    .collect(Collectors.toSet());

            // Filter projections to keep only those with matching document titles
            projections = projections.stream()
                    .filter(p -> filteredDocIds.contains(p.getDocumentId()))
                    .toList();

            if (projections.isEmpty()) {
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        }

        // Map projection to response
        List<AdminDocumentReportResponse> responses = projections.stream()
                .map(projection -> {
                    DocumentInformation doc = documentMap.get(projection.getDocumentId());

                    // Skip if document not found or doesn't match title filter
                    if (doc == null) return null;

                    return new AdminDocumentReportResponse(
                            projection.getDocumentId(),
                            doc.getFilename(),
                            UUID.fromString(doc.getUserId()),
                            getUsernameById(UUID.fromString(doc.getUserId())),
                            projection.getStatus(),
                            projection.getProcessed() != null ? projection.getProcessed() : false,
                            projection.getReportCount(),
                            projection.getUpdatedBy(),
                            projection.getUpdatedBy() != null ?
                                    getUsernameById(projection.getUpdatedBy()) : null,
                            projection.getUpdatedAt()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Count the total records for pagination
        long totalElements = documentTitle == null ?
                documentReportRepository.countDocumentReportsGroupedByProcessed(statusStr, fromDate, toDate, reportTypeCode) :
                responses.size();

        return new PageImpl<>(responses, pageable, totalElements);
    }

    @Transactional(readOnly = true)
    public List<DocumentReportDetail> getDocumentReportDetails(String documentId, Boolean processed) {
        List<DocumentReport> reports = documentReportRepository.findByDocumentIdAndProcessed(documentId, processed);

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