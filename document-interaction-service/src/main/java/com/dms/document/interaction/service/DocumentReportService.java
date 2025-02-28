package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.enums.ReportStatus;
import com.dms.document.interaction.mapper.ReportTypeMapper;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentReport;
import com.dms.document.interaction.model.MasterData;
import com.dms.document.interaction.repository.DocumentReportRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.MasterDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        if (documentReportRepository.existsByDocumentIdAndUserId(documentId, userResponse.userId())) {
            throw new IllegalStateException("You have already reported this document");
        }

        // Create and save report
        DocumentReport report = new DocumentReport();
        report.setDocumentId(documentId);
        report.setUserId(userResponse.userId());
        report.setReportTypeCode(reportType.getCode());
        report.setDescription(request.description());
        report.setStatus(ReportStatus.PENDING);
        report.setCreatedAt(Instant.now());

        DocumentReport savedReport = documentReportRepository.save(report);
        return reportTypeMapper.mapToResponse(savedReport, reportType);
    }

    @Transactional
    public void updateReportStatus(String documentId, ReportStatus newStatus, String username) {
        UserResponse resolver = getUserFromUsername(username);
        if (!resolver.role().roleName().equals(AppRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Only administrators can update report status");
        }

        // Update status for related reports
        documentReportRepository.updateStatusForDocument(documentId, newStatus,
                resolver.userId(), Instant.now());

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

        return documentReportRepository.findByDocumentIdAndUserId(documentId, userResponse.userId())
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
            ReportStatus status,
            String reportTypeCode,
            Pageable pageable) {

        // 1. First, get unique document IDs with pagination
        String statusStr = status != null ? status.name() : null;
        Page<String> documentIdPage = documentReportRepository.findDistinctDocumentIdsWithFilters(statusStr, fromDate, toDate, reportTypeCode, pageable);

        List<String> documentIds = documentIdPage.getContent();
        if (documentIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // Load all documents for these IDs
        List<DocumentInformation> documents = documentRepository.findAllById(documentIds);

        // Filter by document title if specified
        if (StringUtils.isNotEmpty(documentTitle)) {
            documents = documents.stream()
                    .filter(doc -> StringUtils.containsIgnoreCase(doc.getFilename(), documentTitle))
                    .toList();

            // Update document IDs to only include filtered documents
            documentIds = documents.stream()
                    .map(DocumentInformation::getId)
                    .collect(Collectors.toList());

            if (documentIds.isEmpty()) {
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        }

        // Create a map of document ID to document for easy lookup
        Map<String, DocumentInformation> documentMap = documents.stream()
                .collect(Collectors.toMap(DocumentInformation::getId, Function.identity()));

        // Load primary reports to get status and resolved info (only one per document)
        List<DocumentReport> primaryReports = new ArrayList<>();
        Map<String, DocumentReport> primaryReportMap = new HashMap<>();

        // For each document ID, find the latest report
        for (String docId : documentIds) {
            List<DocumentReport> reports = documentReportRepository.findByDocumentId(docId);
            if (!reports.isEmpty()) {
                // Sort reports by creation date (newest first)
                reports.sort(Comparator.comparing(DocumentReport::getCreatedAt).reversed());
                DocumentReport primaryReport = reports.get(0);
                primaryReports.add(primaryReport);
                primaryReportMap.put(docId, primaryReport);
            }
        }

        // Create response without report details
        List<AdminDocumentReportResponse> responses = new ArrayList<>();
        for (String docId : documentIds) {
            DocumentInformation doc = documentMap.get(docId);
            if (doc == null) continue;

            DocumentReport primaryReport = primaryReportMap.get(docId);
            if (primaryReport == null) continue;

            // Count reports for this document
            int reportCount = documentReportRepository.countByDocumentId(docId);

            responses.add(new AdminDocumentReportResponse(
                    docId,
                    doc.getFilename(),
                    UUID.fromString(doc.getUserId()),
                    getUsernameById(UUID.fromString(doc.getUserId())),
                    primaryReport.getStatus(),
                    reportCount,
                    primaryReport.getUpdatedBy(),
                    primaryReport.getUpdatedBy() != null ?
                            getUsernameById(primaryReport.getUpdatedBy()) : null,
                    primaryReport.getUpdatedAt()
            ));
        }

        // Get total distinct document IDs that match criteria
        long totalDocuments = documentReportRepository.countDistinctDocumentIdsWithFilters(statusStr, fromDate, toDate, reportTypeCode);

        return new PageImpl<>(responses, pageable, totalDocuments);
    }

    @Transactional(readOnly = true)
    public List<DocumentReportDetail> getDocumentReportDetails(String documentId) {
        List<DocumentReport> reports = documentReportRepository.findByDocumentId(documentId);

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