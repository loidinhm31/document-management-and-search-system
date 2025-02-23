package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.DocumentStatisticsResponse;
import com.dms.document.interaction.enums.UserDocumentActionType;
import com.dms.document.interaction.model.ActionCountResult;
import com.dms.document.interaction.repository.UserDocumentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentHistoryService {
    private final UserDocumentHistoryRepository userDocumentHistoryRepository;

    @Transactional(readOnly = true)
    public DocumentStatisticsResponse getDocumentStatistics(String documentId) {
        // Get all action counts in a single query
        List<ActionCountResult> actionCounts = userDocumentHistoryRepository.getActionCountsForDocument(documentId);

        // Convert to map for easier lookup
        Map<String, Integer> countsByType = actionCounts.stream()
                .collect(Collectors.toMap(
                        ActionCountResult::getActionType,
                        ActionCountResult::getCount,
                        (a, b) -> a  // In case of duplicates, keep first value
                ));

        // Helper function to safely get count
        Function<String, Integer> getCount = type -> countsByType.getOrDefault(type, 0);

        // Calculate total interactions
        int totalInteractions = countsByType.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        // Build response DTO
        return new DocumentStatisticsResponse(
                getCount.apply(UserDocumentActionType.VIEW_DOCUMENT.name()),
                getCount.apply(UserDocumentActionType.UPDATE_DOCUMENT.name()) +
                getCount.apply(UserDocumentActionType.UPDATE_DOCUMENT_FILE.name()),
                getCount.apply(UserDocumentActionType.DELETE_DOCUMENT.name()),
                getCount.apply(UserDocumentActionType.DOWNLOAD_FILE.name()) +
                getCount.apply(UserDocumentActionType.DOWNLOAD_VERSION.name()),
                getCount.apply(UserDocumentActionType.REVERT_VERSION.name()),
                getCount.apply(UserDocumentActionType.SHARE.name()),
                getCount.apply(UserDocumentActionType.FAVORITE.name()),
                getCount.apply(UserDocumentActionType.COMMENT.name()),
                totalInteractions
        );
    }
}