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
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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

    @Transactional(readOnly = true)
    public Page<AdminCommentReportResponse> getAdminCommentReports(
            CommentReportSearchRequest searchRequest,
            Pageable pageable) {

        Page<CommentReport> reports = commentReportRepository.findAllWithFilters(
                searchRequest.commentContent(),
                searchRequest.reportTypeCode(),
                searchRequest.createdFrom(),
                searchRequest.createdTo(),
                searchRequest.resolved(),
                pageable
        );

        return reports.map(this::mapToAdminResponse);
    }

    @Transactional
    public AdminCommentReportResponse resolveCommentReport(
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

        CommentReport savedReport = commentReportRepository.save(report);


        // Resolve actual comment
        documentCommentRepository.findByDocumentIdAndId(report.getDocumentId(), report.getCommentId())
                .ifPresent(document -> {
                    document.setContent("[deleted]");
                    document.setUpdatedAt(Instant.now());
                });
        return mapToAdminResponse(savedReport);
    }

    private UserResponse getUserFromUsername(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        return response.getBody();
    }

    private AdminCommentReportResponse mapToAdminResponse(CommentReport report) {
        // Get master data for report type
        MasterData reportType = masterDataRepository.findByTypeAndCode(
                MasterDataType.REPORT_COMMENT_TYPE,
                report.getReportTypeCode()
        ).orElseThrow(() -> new IllegalStateException("Report type not found"));

        // Get comment details
        DocumentComment comment = documentCommentRepository.findById(report.getCommentId())
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));

        // Get usernames (in a real application, consider batching these requests)
        String reporterUsername = getUsernameById(report.getUserId());
        String commentUsername = getUsernameById(comment.getUserId());
        String resolvedByUsername = report.getResolvedBy() != null ?
                getUsernameById(report.getResolvedBy()) : null;

        // Create translation DTO
        TranslationDTO translation = new TranslationDTO();
        translation.setEn(reportType.getTranslations().getEn());
        translation.setVi(reportType.getTranslations().getVi());

        return new AdminCommentReportResponse(
                report.getId(),
                report.getDocumentId(),
                report.getCommentId(),
                comment.getContent(),
                report.getUserId(),
                reporterUsername,
                comment.getUserId(),
                commentUsername,
                report.getReportTypeCode(),
                translation,
                report.getDescription(),
                report.isResolved(),
                report.getResolvedBy(),
                resolvedByUsername,
                report.getCreatedAt(),
                report.getResolvedAt()
        );
    }

    private String getUsernameById(UUID userId) {
        try {
            // In a real application, consider implementing caching or batch fetching
            List<UserResponse> users = userClient.getUsersByIds(List.of(userId)).getBody();
            return users != null && !users.isEmpty() ? users.get(0).username() : "Unknown";
        } catch (Exception e) {
            log.warn("Failed to fetch username for user ID: {}", userId, e);
            return "Unknown";
        }
    }
}
