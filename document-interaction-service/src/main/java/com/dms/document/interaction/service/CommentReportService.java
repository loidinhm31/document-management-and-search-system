package com.dms.document.interaction.service;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.*;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.MasterDataType;
import com.dms.document.interaction.mapper.ReportTypeMapper;
import com.dms.document.interaction.model.CommentReport;
import com.dms.document.interaction.model.DocumentComment;
import com.dms.document.interaction.model.MasterData;
import com.dms.document.interaction.repository.CommentReportRepository;
import com.dms.document.interaction.repository.DocumentCommentRepository;
import com.dms.document.interaction.repository.MasterDataRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
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
        if (commentReportRepository.existsByCommentIdAndUserId(commentId, userResponse.userId())) {
            throw new IllegalStateException("You have already reported this comment");
        }

        // Create and save report
        CommentReport report = new CommentReport();
        report.setDocumentId(documentId);
        report.setCommentId(commentId);
        report.setUserId(userResponse.userId());
        report.setReportTypeCode(reportType.getCode());
        report.setDescription(request.description());
        report.setResolved(false);
        report.setCreatedAt(Instant.now());
        report.setComment(documentComment);

        CommentReport savedReport = commentReportRepository.save(report);

        return reportTypeMapper.mapToResponse(savedReport, reportType);
    }

    @Transactional(readOnly = true)
    public Optional<CommentReportResponse> getUserReport(String documentId, Long commentId, String username) {
        UserResponse userResponse = getUserFromUsername(username);

        return commentReportRepository.findByDocumentIdAndCommentIdAndUserId(documentId, commentId, userResponse.userId())
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
            Long reportId,
            boolean resolved,
            String adminUsername) {
        UserResponse admin = getUserFromUsername(adminUsername);
        if (!admin.role().roleName().equals(AppRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Only administrators can resolve reports");
        }

        CommentReport report = commentReportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("Report not found"));

        report.setResolved(resolved);
        report.setResolvedBy(admin.userId());
        report.setResolvedAt(Instant.now());

        commentReportRepository.save(report);

        // Resolve actual comment
        documentCommentRepository.findByDocumentIdAndId(report.getDocumentId(), report.getCommentId())
                .ifPresent(document -> {
                    document.setContent("[deleted]");
                    document.setUpdatedAt(Instant.now());
                });
    }

    @Transactional(readOnly = true)
    public Page<AdminCommentReportResponse> getAdminCommentReports(
            Instant fromDate,
            Instant toDate,
            String commentContent,
            String reportTypeCode,
            Boolean resolved,
            Pageable pageable) {

        // Fetch comment reports with pagination and filters
        Page<CommentReport> reports = commentReportRepository.findAllWithFilters(
                fromDate,
                toDate,
                commentContent,
                reportTypeCode,
                resolved,
                pageable
        );

        List<CommentReport> reportsList = reports.getContent();
        if (reportsList.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // Extract all comment IDs for batch loading
        Set<Long> commentIds = reportsList.stream()
                .map(CommentReport::getCommentId)
                .collect(Collectors.toSet());

        // Batch fetch all comments
        List<DocumentComment> comments = documentCommentRepository.findAllById(commentIds);
        Map<Long, DocumentComment> commentsMap = comments.stream()
                .collect(Collectors.toMap(
                        DocumentComment::getId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        // Group reports by comment ID to ensure uniqueness and count reports
        Map<Long, List<CommentReport>> reportsByCommentId = reportsList.stream()
                .collect(Collectors.groupingBy(CommentReport::getCommentId));

        // Extract all user IDs (reporters, comment creators, resolvers)
        Set<UUID> userIds = new HashSet<>();

        // Add reporter user IDs
        reportsList.forEach(report -> {
            userIds.add(report.getUserId()); // Reporter
            if (report.getResolvedBy() != null) {
                userIds.add(report.getResolvedBy()); // Resolver if exists
            }
        });

        // Add comment author user IDs
        comments.forEach(comment -> userIds.add(comment.getUserId()));

        // Batch fetch all user information
        Map<UUID, String> usernamesMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                ResponseEntity<List<UserResponse>> usersResponse = userClient.getUsersByIds(new ArrayList<>(userIds));
                if (usersResponse.getBody() != null) {
                    usernamesMap = usersResponse.getBody().stream()
                            .collect(Collectors.toMap(
                                    UserResponse::userId,
                                    UserResponse::username,
                                    (existing, replacement) -> existing
                            ));
                }
            } catch (Exception e) {
                log.warn("Failed to fetch user details: {}", e.getMessage());
            }
        }

        // Map to response objects using the pre-loaded data
        Map<UUID, String> finalUsernamesMap = usernamesMap;
        List<AdminCommentReportResponse> responseList = new ArrayList<>();

        // Process each comment ID with its associated reports
        for (Map.Entry<Long, List<CommentReport>> entry : reportsByCommentId.entrySet()) {
            Long commentId = entry.getKey();
            List<CommentReport> commentReports = entry.getValue();

            // Use the most recent report as the representative (for details like status)
            CommentReport primaryReport = commentReports.stream()
                    .max(Comparator.comparing(CommentReport::getCreatedAt))
                    .orElse(commentReports.get(0));

            DocumentComment comment = commentsMap.get(commentId);
            String commentContentObj = comment != null ? comment.getContent() : "[Comment not found]";
            UUID commentUserId = comment != null ? comment.getUserId() : null;

            // Create response with report count
            AdminCommentReportResponse response = new AdminCommentReportResponse(
                    primaryReport.getId(),
                    primaryReport.getDocumentId(),
                    primaryReport.getCommentId(),
                    commentContentObj,
                    primaryReport.getUserId(),
                    finalUsernamesMap.getOrDefault(primaryReport.getUserId(), "Unknown"),
                    commentUserId,
                    commentUserId != null ? finalUsernamesMap.getOrDefault(commentUserId, "Unknown") : "Unknown",
                    primaryReport.getReportTypeCode(),
                    primaryReport.getDescription(),
                    primaryReport.isResolved(),
                    primaryReport.getResolvedBy(),
                    primaryReport.getResolvedBy() != null ?
                            finalUsernamesMap.getOrDefault(primaryReport.getResolvedBy(), "Unknown") : null,
                    primaryReport.getCreatedAt(),
                    primaryReport.getResolvedAt(),
                    commentReports.size()
            );

            responseList.add(response);
        }

        return new PageImpl<>(responseList, pageable, reports.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CommentReportDetailResponse getCommentReportDetail(Long reportId) {
        CommentReport report = commentReportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("Comment report not found"));

        // Get comment content
        DocumentComment comment = documentCommentRepository.findById(report.getCommentId())
                .orElse(null);

        String commentContent = comment != null ? comment.getContent() : "[Comment not found]";

        // Get usernames for reporter and resolver (if applicable)
        Set<UUID> userIds = new HashSet<>();
        userIds.add(report.getUserId()); // Reporter

        if (comment != null) {
            userIds.add(comment.getUserId()); // Comment author
        }

        if (report.getResolvedBy() != null) {
            userIds.add(report.getResolvedBy());
        }

        Map<UUID, String> usernames = batchFetchUsernames(userIds);

        return CommentReportDetailResponse.builder()
                .id(report.getId())
                .documentId(report.getDocumentId())
                .commentId(report.getCommentId())
                .commentContent(commentContent)
                .reporterUserId(report.getUserId())
                .reporterUsername(usernames.getOrDefault(report.getUserId(), "Unknown"))
                .commentUserId(comment != null ? comment.getUserId() : null)
                .commentUsername(comment != null ? usernames.getOrDefault(comment.getUserId(), "Unknown") : "Unknown")
                .reportTypeCode(report.getReportTypeCode())
                .description(report.getDescription())
                .resolved(report.isResolved())
                .resolvedBy(report.getResolvedBy())
                .resolvedByUsername(report.getResolvedBy() != null ?
                        usernames.getOrDefault(report.getResolvedBy(), "Unknown") : null)
                .createdAt(report.getCreatedAt())
                .resolvedAt(report.getResolvedAt())
                .build();
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
