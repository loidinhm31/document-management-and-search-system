package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.AggregatedInteractionStats;
import com.dms.document.interaction.dto.UpdateDocumentPreferencesRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.InteractionType;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentInteraction;
import com.dms.document.interaction.model.DocumentPreferences;
import com.dms.document.interaction.repository.DocumentInteractionRepository;
import com.dms.document.interaction.repository.DocumentPreferencesRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.service.DocumentPreferencesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentPreferencesServiceImpl implements DocumentPreferencesService {
    private final DocumentPreferencesRepository documentPreferencesRepository;
    private final DocumentInteractionRepository documentInteractionRepository;
    private final DocumentRepository documentRepository;

    private static final int MAX_RECENT_DOCUMENTS = 50;
    private static final int DAYS_FOR_RECENT_INTERACTIONS = 30;
    private static final double FAVORITE_WEIGHT = 3.0;
    private static final double COMMENT_WEIGHT = 2.0;
    private static final double DOWNLOAD_WEIGHT = 2.0;
    private static final double VIEW_WEIGHT = 1.0;
    private final UserClient userClient;

    @Transactional(readOnly = true)
    @Override
    public DocumentPreferences getDocumentPreferences(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        return documentPreferencesRepository.findByUserId(userResponse.userId().toString())
                .orElseGet(() -> createDefaultPreferences(userResponse.userId().toString()));
    }

    @Transactional
    @Override
    public DocumentPreferences updateExplicitPreferences(String userId, UpdateDocumentPreferencesRequest request) {
        DocumentPreferences existing = getDocumentPreferences(userId);

        // Update explicit preferences while preserving implicit data
        existing.setPreferredMajors(request.preferredMajors());
        existing.setPreferredCourseCodes(request.preferredCourseCodes());
        existing.setPreferredLevels(request.preferredLevels());
        existing.setPreferredCategories(request.preferredCategories());
        existing.setPreferredTags(request.preferredTags());
        existing.setLanguagePreferences(request.languagePreferences());

        existing.setUpdatedAt(Instant.now());
        return documentPreferencesRepository.save(existing);
    }

    @Transactional
    @Override
    public void recordInteraction(UUID userId, String documentId, InteractionType type) {
        // Validate document access
        DocumentInformation document = documentRepository.findAccessibleDocumentByIdAndUserId(
                documentId,
                userId.toString()
        ).orElseThrow(() -> new IllegalArgumentException("Document not found or not accessible"));

        // Find or create interaction document
        DocumentInteraction interaction = documentInteractionRepository
                .findByUserIdAndDocumentId(userId.toString(), documentId)
                .orElseGet(() -> {
                    DocumentInteraction newInteraction = new DocumentInteraction();
                    newInteraction.setUserId(userId.toString());
                    newInteraction.setDocumentId(documentId);
                    newInteraction.setInteractions(new HashMap<>());
                    newInteraction.setFirstInteractionDate(Instant.now());
                    return newInteraction;
                });

        // Update interaction stats
        Map<String, DocumentInteraction.InteractionStats> stats = interaction.getInteractions();
        DocumentInteraction.InteractionStats typeStats = stats.computeIfAbsent(
                type.name(),
                k -> new DocumentInteraction.InteractionStats()
        );

        typeStats.setCount(typeStats.getCount() + 1);
        typeStats.setLastUpdate(Instant.now());
        interaction.setLastInteractionDate(Instant.now());

        documentInteractionRepository.save(interaction);

        // Update preferences based on interaction
        updateImplicitPreferences(userId, document, type);
    }


    @Transactional
    @Override
    public void updateImplicitPreferences(UUID userId, DocumentInformation document,
                                          InteractionType type) {
        DocumentPreferences preferences = documentPreferencesRepository.findByUserId(userId.toString())
                .orElseGet(() -> createDefaultPreferences(userId.toString()));

        // Update recent views for view/download interactions
        if (type == InteractionType.VIEW ||
            type == InteractionType.DOWNLOAD) {
            updateRecentDocuments(preferences, document.getId());
        }

        // Update interaction counts
        updateInteractionCounts(preferences, document);

        // Calculate new content type weights
        Map<String, Double> newWeights = calculateContentTypeWeights(userId);
        preferences.setContentTypeWeights(newWeights);

        preferences.setUpdatedAt(Instant.now());
        documentPreferencesRepository.save(preferences);
    }

    private void updateRecentDocuments(DocumentPreferences preferences, String documentId) {
        Set<String> recentDocs = preferences.getRecentViewedDocuments();
        if (recentDocs == null) {
            recentDocs = new LinkedHashSet<>();
        }

        // Remove if already exists (to move to end)
        recentDocs.remove(documentId);

        // Remove oldest if at capacity
        if (recentDocs.size() >= MAX_RECENT_DOCUMENTS) {
            recentDocs.remove(recentDocs.iterator().next());
        }

        // Add to end
        recentDocs.add(documentId);
        preferences.setRecentViewedDocuments(recentDocs);
    }

    private void updateInteractionCounts(DocumentPreferences preferences, DocumentInformation document) {
        // Update major counts
        Map<String, Integer> majorCounts = preferences.getMajorInteractionCounts();
        if (majorCounts == null) {
            majorCounts = new HashMap<>();
        }

        if (CollectionUtils.isNotEmpty(document.getMajors())) {
            for (String major : document.getMajors()) {
                majorCounts.merge(major, 1, Integer::sum);
            }
        }

        preferences.setMajorInteractionCounts(majorCounts);

        // Update course code counts
        Map<String, Integer> courseCodeCounts = preferences.getCourseCodeInteractionCounts();
        if (courseCodeCounts == null) {
            courseCodeCounts = new HashMap<>();
        }

        if (CollectionUtils.isNotEmpty(document.getCourseCodes())) {
            for (String course : document.getCourseCodes()) {
                courseCodeCounts.merge(course, 1, Integer::sum);
            }
        }

        preferences.setCourseCodeInteractionCounts(courseCodeCounts);

        // Update level counts
        Map<String, Integer> levelCounts = preferences.getLevelInteractionCounts();
        if (levelCounts == null) {
            levelCounts = new HashMap<>();
        }
        levelCounts.merge(document.getCourseLevel(), 1, Integer::sum);
        preferences.setLevelInteractionCounts(levelCounts);

        // Update category counts
        Map<String, Integer> categoryCounts = preferences.getCategoryInteractionCounts();
        if (categoryCounts == null) {
            categoryCounts = new HashMap<>();
        }

        if (CollectionUtils.isNotEmpty(document.getCategories())) {
            for (String category : document.getCategories()) {
                categoryCounts.merge(category, 1, Integer::sum);
            }
        }

        preferences.setCategoryInteractionCounts(categoryCounts);

        // Update tag counts
        Map<String, Integer> tagCounts = preferences.getTagInteractionCounts();
        if (tagCounts == null) {
            tagCounts = new HashMap<>();
        }
        if (document.getTags() != null) {
            for (String tag : document.getTags()) {
                tagCounts.merge(tag, 1, Integer::sum);
            }
        }
        preferences.setTagInteractionCounts(tagCounts);
    }

    @Override
    public Map<String, Double> getCalculateContentTypeWeights(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        return calculateContentTypeWeights(userResponse.userId());
    }

    protected Map<String, Double> calculateContentTypeWeights(UUID userId) {
        Date recentDate = Date.from(Instant.now().minus(DAYS_FOR_RECENT_INTERACTIONS, ChronoUnit.DAYS));
        List<DocumentInteraction> recentInteractions =
                documentInteractionRepository.findRecentInteractions(userId.toString(), recentDate);

        Map<String, Map<String, Double>> typeWeights = new HashMap<>();

        for (DocumentInteraction interaction : recentInteractions) {
            DocumentInformation doc = documentRepository.findById(interaction.getDocumentId())
                    .orElse(null);

            if (doc != null) {
                String docType = doc.getDocumentType().name();
                Map<String, Double> weights = typeWeights.computeIfAbsent(docType, k -> new HashMap<>());

                // Calculate weights based on interaction counts
                Map<String, DocumentInteraction.InteractionStats> stats = interaction.getInteractions();

                weights.merge(InteractionType.VIEW.name(),
                        getInteractionCount(stats, InteractionType.VIEW.name()) * VIEW_WEIGHT, Double::sum);
                weights.merge(InteractionType.DOWNLOAD.name(),
                        getInteractionCount(stats, InteractionType.DOWNLOAD.name()) * DOWNLOAD_WEIGHT, Double::sum);
                weights.merge(InteractionType.COMMENT.name(),
                        getInteractionCount(stats, InteractionType.COMMENT.name()) * COMMENT_WEIGHT, Double::sum);
                weights.merge(InteractionType.SHARE.name(),
                        getInteractionCount(stats, InteractionType.SHARE.name()) * FAVORITE_WEIGHT, Double::sum);
            }
        }

        // Normalize weights
        return normalizeWeights(typeWeights);
    }

    @Transactional
    @Override
    public DocumentPreferences createDefaultPreferences(String userId) {
        DocumentPreferences preferences = new DocumentPreferences();
        preferences.setUserId(userId);

        // Initialize empty collections
        preferences.setPreferredMajors(new HashSet<>());
        preferences.setPreferredCourseCodes(new HashSet<>());
        preferences.setPreferredLevels(new HashSet<>());
        preferences.setPreferredCategories(new HashSet<>());
        preferences.setPreferredTags(new HashSet<>());

        // Set default language to English
        preferences.setLanguagePreferences(new HashSet<>(Collections.singletonList("en")));

        // Initialize tracking maps
        preferences.setContentTypeWeights(new HashMap<>());
        preferences.setCategoryInteractionCounts(new HashMap<>());
        preferences.setTagInteractionCounts(new HashMap<>());
        preferences.setMajorInteractionCounts(new HashMap<>());

        // Initialize recent documents
        preferences.setRecentViewedDocuments(new LinkedHashSet<>());

        // Set timestamps
        preferences.setCreatedAt(Instant.now());
        preferences.setUpdatedAt(Instant.now());

        return documentPreferencesRepository.save(preferences);
    }

    @Transactional(readOnly = true)
    @Override
    public Set<String> getRecommendedTags(String userId) {
        DocumentPreferences preferences = getDocumentPreferences(userId);
        Map<String, Integer> tagCounts = preferences.getTagInteractionCounts();

        if (tagCounts == null || tagCounts.isEmpty()) {
            return Collections.emptySet();
        }

        return tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> getInteractionStatistics(String username) {
        ResponseEntity<UserResponse> response = userClient.getUserByUsername(username);
        if (!response.getStatusCode().is2xxSuccessful() || Objects.isNull(response.getBody())) {
            throw new InvalidDataAccessResourceUsageException("User not found");
        }
        UserResponse userResponse = response.getBody();

        Date recentDate = Date.from(Instant.now()
                .minus(DAYS_FOR_RECENT_INTERACTIONS, ChronoUnit.DAYS));

        AggregatedInteractionStats stats =
                documentInteractionRepository.getAggregatedStats(userResponse.userId().toString(), recentDate);

        Map<String, Object> result = new HashMap<>();
        if (Objects.nonNull(stats)) {
            Map<InteractionType, Long> interactionCounts = new EnumMap<>(InteractionType.class);

            interactionCounts.put(InteractionType.VIEW, stats.getTotalViews());
            interactionCounts.put(InteractionType.DOWNLOAD, stats.getTotalDownloads());
            interactionCounts.put(InteractionType.COMMENT, stats.getTotalComments());
            interactionCounts.put(InteractionType.SHARE, stats.getTotalShares());

            result.put("interactionCounts", interactionCounts);
            result.put("uniqueDocumentsAccessed", stats.getUniqueDocuments());

        }
        return result;
    }

    private double getInteractionCount(Map<String, DocumentInteraction.InteractionStats> stats,
                                       String type) {
        return Optional.ofNullable(stats.get(type))
                .map(DocumentInteraction.InteractionStats::getCount)
                .orElse(0);
    }

    private Map<String, Double> normalizeWeights(Map<String, Map<String, Double>> typeWeights) {
        Map<String, Double> normalizedWeights = new HashMap<>();

        // Calculate total weight for each document type
        typeWeights.forEach((docType, interactionWeights) -> {
            double totalTypeWeight = interactionWeights.values()
                    .stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
            normalizedWeights.put(docType, totalTypeWeight);
        });

        // Calculate the sum of all weights
        double totalSum = normalizedWeights.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        // If there are no interactions, return equal weights
        if (totalSum == 0) {
            double equalWeight = 1.0 / typeWeights.size();
            typeWeights.keySet().forEach(type ->
                    normalizedWeights.put(type, equalWeight)
            );
            return normalizedWeights;
        }

        // Normalize weights to sum to 1.0
        normalizedWeights.forEach((docType, weight) -> {
            double normalizedWeight = weight / totalSum;
            // Apply min threshold to ensure no weight is too small
            normalizedWeight = Math.max(normalizedWeight, 0.01);
            normalizedWeights.put(docType, normalizedWeight);
        });

        // Re-normalize after applying minimum threshold
        double finalSum = normalizedWeights.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        // Final normalization to ensure sum is exactly 1.0
        normalizedWeights.forEach((docType, weight) ->
                normalizedWeights.put(docType, weight / finalSum)
        );

        return normalizedWeights;
    }
}