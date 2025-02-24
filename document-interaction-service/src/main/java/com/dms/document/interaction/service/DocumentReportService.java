package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.EventType;
import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.enums.ReportStatus;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentReport;
import com.dms.document.interaction.model.MasterData;
import com.dms.document.interaction.repository.DocumentReportRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.MasterDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentReportService {
    private final DocumentReportRepository documentReportRepository;
    private final DocumentRepository documentRepository;
    private final MasterDataRepository masterDataRepository;
    private final UserClient userClient;
    private final PublishEventService publishEventService;

    @Transactional
    public ReportResponse createReport(String documentId, ReportRequest request, String username) {
        UserResponse userResponse = getUserFromUsername(username);

        // Verify document exists
        documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userResponse.userId().toString())
                .orElseThrow(() -> new IllegalArgumentException("Document not found or not accessible"));

        // Verify report type exists in master data
        MasterData reportType = masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_TYPE, request.reportTypeCode())
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


        DocumentReport savedReport = documentReportRepository.save(report);
        return mapToResponse(savedReport, reportType);
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
                                .subject(EventType.REPORT_PROCESS_EVENT.name())
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
                            MasterDataType.REPORT_TYPE,
                            report.getReportTypeCode()
                    ).orElseThrow(() -> new IllegalStateException("Report type not found"));
                    return mapToResponse(report, reportType);
                });
    }

    @Transactional(readOnly = true)
    public List<ReportTypeResponse> getReportTypes() {
        return masterDataRepository.findByTypeAndIsActive(MasterDataType.REPORT_TYPE, true)
                .stream()
                .map(this::mapToReportTypeResponse)
                .collect(Collectors.toList());
    }

    public Page<ReportResponse> getAllDocumentReports() {
        // TODO
        return Page.empty();
    }

    private UserResponse getUserFromUsername(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        return response.getBody();
    }

    private ReportResponse mapToResponse(DocumentReport report, MasterData reportType) {
        TranslationDTO translation = new TranslationDTO();
        translation.setEn(reportType.getTranslations().getEn());
        translation.setVi(reportType.getTranslations().getVi());

        return new ReportResponse(
                report.getId(),
                report.getDocumentId(),
                report.getReportTypeCode(),
                translation,
                report.getDescription(),
                report.getCreatedAt()
        );
    }

    private ReportTypeResponse mapToReportTypeResponse(MasterData masterData) {
        TranslationDTO translation = new TranslationDTO();
        translation.setEn(masterData.getTranslations().getEn());
        translation.setVi(masterData.getTranslations().getVi());

        return new ReportTypeResponse(
                masterData.getCode(),
                translation,
                masterData.getDescription()
        );
    }
}