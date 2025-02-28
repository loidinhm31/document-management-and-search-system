package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.CommentReportStatus;
import com.dms.document.interaction.enums.DocumentReportStatus;
import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.mapper.ReportTypeMapper;
import com.dms.document.interaction.model.CommentReport;
import com.dms.document.interaction.model.DocumentComment;
import com.dms.document.interaction.model.DocumentReport;
import com.dms.document.interaction.model.MasterData;
import com.dms.document.interaction.model.projection.CommentReportProjection;
import com.dms.document.interaction.repository.CommentReportRepository;
import com.dms.document.interaction.repository.DocumentCommentRepository;
import com.dms.document.interaction.repository.MasterDataRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentReportService {
    private final CommentReportRepository commentReportRepository;
    private final DocumentCommentRepository documentCommentRepository;
    private final MasterDataRepository masterDataRepository;
    private final UserClient userClient;
    private final ReportTypeMapper reportTypeMapper;
    private final DocumentNotificationService documentNotificationService;

    @Transactional
    public CommentReportResponse createReport(String documentId, Long commentId, CommentReportRequest request, String username) {
        // Get user information
        UserResponse userResponse = getUserFromUsername(username);

        // Verify comment exists
        DocumentComment documentComment = documentCommentRepository.findByDocumentIdAndId(documentId, commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

        // Verify report type exists in master data
        MasterData reportType = masterDataRepository.findByTypeAndCode(MasterDataType.REPORT_COMMENT_TYPE, request.reportTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid report type"));

        // Check if user has already reported this comment
        if (commentReportRepository.existsByCommentIdAndUserIdAndProcessed(commentId, userResponse.userId(), Boolean.FALSE)) {
            throw new IllegalStateException("You have already reported this comment");
        }

        // Find user's processed report if it exists
        List<CommentReport> processedReports = commentReportRepository.findByCommentIdAndProcessed(commentId, Boolean.TRUE);
        Optional<CommentReport> maxTimeProcessedReport = processedReports.stream()
                .max(Comparator.comparing(report -> report.getTimes() != null ? report.getTimes() : 0));

        int times = 1;
        if (maxTimeProcessedReport.isPresent()) {
            // Update existing report by incrementing times
            CommentReport maxTimeReport = maxTimeProcessedReport.get();
            times = Objects.nonNull(maxTimeReport.getTimes()) ? maxTimeReport.getTimes() + 1 : 1;
        }

        // Create and save report
        CommentReport report = new CommentReport();
        report.setDocumentId(documentId);
        report.setCommentId(commentId);
        report.setUserId(userResponse.userId());
        report.setReportTypeCode(reportType.getCode());
        report.setDescription(request.description());
        report.setStatus(CommentReportStatus.PENDING);
        report.setProcessed(Boolean.FALSE);
        report.setTimes(times);
        report.setCreatedAt(Instant.now());
        report.setComment(documentComment);

        CommentReport savedReport = commentReportRepository.save(report);
        return reportTypeMapper.mapToResponse(savedReport, reportType);
    }

    @Transactional(readOnly = true)
    public Optional<CommentReportResponse> getUserReport(String documentId, Long commentId, String username) {
        UserResponse userResponse = getUserFromUsername(username);

        return commentReportRepository.findByDocumentIdAndCommentIdAndUserIdAndProcessed(documentId, commentId, userResponse.userId(), Boolean.FALSE)
                .map(report -> {
                    MasterData reportType = masterDataRepository.findByTypeAndCode(
                            MasterDataType.REPORT_COMMENT_TYPE,
                            report.getReportTypeCode()
                    ).orElseThrow(() -> new IllegalStateException("Report type not found"));
                    return reportTypeMapper.mapToResponse(report, reportType);
                });
    }

    public List<ReportTypeResponse> getReportTypes() {
        return masterDataRepository.findByTypeAndIsActive(MasterDataType.REPORT_COMMENT_TYPE, true)
                .stream()
                .map(reportTypeMapper::mapToReportTypeResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void resolveCommentReport(
            Long commentId,
            CommentReportStatus newStatus,
            String adminUsername) {
        UserResponse admin = getUserFromUsername(adminUsername);
        if (!admin.role().roleName().equals(AppRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Only administrators can resolve reports");
        }

        List<CommentReport> commentReports = commentReportRepository.findByCommentId(commentId);
        if (commentReports.isEmpty()) {
            throw new EntityNotFoundException("Comment report not found");
        }

        // Find current status of report
        CommentReport currentReport = commentReports
                .stream()
                .max(Comparator.comparing(CommentReport::getCreatedAt))
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));
        if (currentReport.getStatus() == CommentReportStatus.REJECTED || BooleanUtils.isTrue(currentReport.getProcessed())) {
            throw new IllegalStateException("Report has already been processed");
        }

        if (currentReport.getStatus() == newStatus) {
            throw new IllegalStateException("Cannot update the same status");
        }

        AtomicInteger times = new AtomicInteger();
        Instant updatedAt = Instant.now();
        commentReports.forEach(report -> {
            times.set(report.getTimes());
            report.setStatus(newStatus);
            report.setUpdatedBy(admin.userId());
            report.setUpdatedAt(updatedAt);
            if (newStatus == CommentReportStatus.RESOLVED || newStatus == CommentReportStatus.REJECTED) {
                report.setProcessed(Boolean.TRUE);
            }
        });

        // Resolve actual comment
        AtomicReference<String> documentId = new AtomicReference<>();
        documentCommentRepository.findByDocumentIdAndId(commentReports.get(0).getDocumentId(), commentId)
                .ifPresent(dc -> {
                    if (newStatus == CommentReportStatus.RESOLVED) {
                        documentId.set(dc.getDocumentId());
                        dc.setContent("[deleted]");
                        dc.setFlag(-1); // Flag -1 is deleted by reporter
                        dc.setUpdatedAt(Instant.now());
                    } else if (newStatus == CommentReportStatus.REJECTED) {
                        dc.setFlag(1);
                    }
                });

        CompletableFuture.runAsync(() -> {
            // Notify user of resolution
            documentNotificationService.sendCommentReportResolvedNotification(documentId.get(), commentId, admin.userId(), times.get());
        });
    }

    @Transactional(readOnly = true)
    public Page<AdminCommentReportResponse> getAdminCommentReports(
            Instant fromDate,
            Instant toDate,
            String commentContent,
            String reportTypeCode,
            CommentReportStatus status,
            Pageable pageable) {

        String statusStr = status != null ? status.name() : null;

        // Get comment reports grouped by comment ID, processed flag, and status
        Page<CommentReportProjection> reportProjections = commentReportRepository.findCommentReportsGroupedByProcessed(
                fromDate,
                toDate,
                commentContent,
                reportTypeCode,
                statusStr,
                pageable
        );

        List<CommentReportProjection> projections = reportProjections.getContent();
        if (projections.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // Extract all user IDs that we need to fetch usernames for
        Set<UUID> userIds = new HashSet<>();

        projections.forEach(proj -> {
            // Reporter user ID
            if (proj.getReporterId() != null) {
                userIds.add(proj.getReporterId());
            }

            // Comment owner user ID
            if (proj.getCommentUserId() != null) {
                userIds.add(proj.getCommentUserId());
            }

            // Resolver user ID
            if (proj.getUpdatedBy() != null) {
                userIds.add(proj.getUpdatedBy());
            }
        });

        // Batch fetch all user information
        Map<UUID, String> usernamesMap = batchFetchUsernames(userIds);

        // Map projections to response objects
        List<AdminCommentReportResponse> responseList = projections.stream()
                .map(proj -> new AdminCommentReportResponse(
                        proj.getDocumentId(),
                        proj.getCommentId(),
                        proj.getCommentContent(),
                        proj.getReporterId(),
                        usernamesMap.getOrDefault(proj.getReporterId(), "Unknown"),
                        proj.getCommentUserId(),
                        usernamesMap.getOrDefault(proj.getCommentUserId(), "Unknown"),
                        proj.getReportTypeCode(),
                        proj.getDescription(),
                        proj.getProcessed() != null && proj.getProcessed(),
                        proj.getStatus(),
                        proj.getUpdatedBy(),
                        proj.getUpdatedBy() != null ?
                                usernamesMap.getOrDefault(proj.getUpdatedBy(), "Unknown") : null,
                        proj.getCreatedAt(),
                        proj.getUpdatedAt(),
                        proj.getReportCount()
                ))
                .collect(Collectors.toList());

        // Return paginated results
        return new PageImpl<>(responseList, pageable,
                commentReportRepository.countCommentReportsGroupedByProcessed(
                        fromDate, toDate, commentContent, reportTypeCode, statusStr));
    }

    @Transactional(readOnly = true)
    public List<CommentReportDetailResponse> getCommentReportsByCommentId(Long commentId) {
        // Lấy comment từ repository
        DocumentComment comment = documentCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        List<CommentReport> reports = commentReportRepository.findByCommentId(commentId);

        if (reports.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> userIds = new HashSet<>();

        reports.forEach(report -> {
            userIds.add(report.getUserId()); // Reporter
            if (report.getUpdatedBy() != null) {
                userIds.add(report.getUpdatedBy()); // Resolver nếu có
            }
        });

        userIds.add(comment.getUserId());

        Map<UUID, String> usernames = batchFetchUsernames(userIds);

        return reports.stream()
                .map(report -> CommentReportDetailResponse.builder()
                        .id(report.getId())
                        .documentId(report.getDocumentId())
                        .commentId(report.getCommentId())
                        .commentContent(comment.getContent())
                        .reporterUserId(report.getUserId())
                        .reporterUsername(usernames.getOrDefault(report.getUserId(), "Unknown"))
                        .commentUserId(comment.getUserId())
                        .commentUsername(usernames.getOrDefault(comment.getUserId(), "Unknown"))
                        .reportTypeCode(report.getReportTypeCode())
                        .description(report.getDescription())
                        .processed(report.getProcessed())
                        .resolvedBy(report.getUpdatedBy())
                        .resolvedByUsername(report.getUpdatedBy() != null ?
                                usernames.getOrDefault(report.getUpdatedBy(), "Unknown") : null)
                        .createdAt(report.getCreatedAt())
                        .resolvedAt(report.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }


    private Map<UUID, String> batchFetchUsernames(Set<UUID> userIds) {
        try {
            ResponseEntity<List<UserResponse>> response = userClient.getUsersByIds(new ArrayList<>(userIds));
            if (response.getBody() != null) {
                return response.getBody().stream()
                        .collect(Collectors.toMap(
                                UserResponse::userId,
                                UserResponse::username,
                                (existing, replacement) -> existing // In case of duplicates
                        ));
            }
        } catch (Exception e) {
            log.error("Error batch fetching usernames", e);
        }
        return new HashMap<>();
    }


    private UserResponse getUserFromUsername(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        return response.getBody();
    }
}
