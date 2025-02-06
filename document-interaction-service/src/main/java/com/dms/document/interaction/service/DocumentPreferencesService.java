package com.dms.document.interaction.service;

import com.dms.document.interaction.dto.UpdateDocumentPreferencesRequest;
import com.dms.document.interaction.enums.InteractionType;
import com.dms.document.interaction.model.*;
import com.dms.document.interaction.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentPreferencesService {
    private final DocumentPreferencesRepository preferencesRepository;
    private final DocumentInteractionRepository interactionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFavoriteRepository documentFavoriteRepository;
    private final DocumentContentRepository documentContentRepository;

    private static final int MAX_RECENT_DOCUMENTS = 50;
    private static final int DAYS_FOR_RECENT_INTERACTIONS = 30;
    private static final double FAVORITE_WEIGHT = 3.0;
    private static final double COMMENT_WEIGHT = 2.0;
    private static final double DOWNLOAD_WEIGHT = 2.0;
    private static final double VIEW_WEIGHT = 1.0;

    @Transactional(readOnly = true)
    public DocumentPreferences getDocumentPreferences(String userId) {
        return preferencesRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId));
    }

    @Transactional
    public DocumentPreferences updateExplicitPreferences(String userId, UpdateDocumentPreferencesRequest request) {
        DocumentPreferences existing = getDocumentPreferences(userId);

        // Update explicit preferences while preserving implicit data
        existing.setPreferredMajors(request.preferredMajors());
        existing.setPreferredLevels(request.preferredLevels());
        existing.setPreferredCategories(request.preferredCategories());
        existing.setPreferredTags(request.preferredTags());
        existing.setLanguagePreferences(request.languagePreferences());

        existing.setUpdatedAt(new Date());
        return preferencesRepository.save(existing);
    }

    @Transactional
    public void recordInteraction(String userId, String documentId,
                                  InteractionType type,
                                  Long durationSeconds) {
        // Validate document exists and user has access
        DocumentInformation document = documentRepository.findAccessibleDocumentByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found or not accessible"));

        // Record the interaction
        DocumentInteraction interaction = new DocumentInteraction();
        interaction.setUserId(userId);
        interaction.setDocumentId(documentId);
        interaction.setInteractionType(type);
        interaction.setCreatedAt(new Date());

        interactionRepository.save(interaction);

        // Update document preferences based on interaction
        updateImplicitPreferences(userId, document, type);
    }

    @Transactional
    public void updateImplicitPreferences(String userId, DocumentInformation document,
                                          InteractionType type) {
        DocumentPreferences preferences = getDocumentPreferences(userId);

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

        preferences.setUpdatedAt(new Date());
        preferencesRepository.save(preferences);
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
        // Update category counts
        Map<String, Integer> categoryCounts = preferences.getCategoryInteractionCounts();
        if (categoryCounts == null) {
            categoryCounts = new HashMap<>();
        }
        categoryCounts.merge(document.getCategory(), 1, Integer::sum);
        preferences.setCategoryInteractionCounts(categoryCounts);

        // Update major counts
        Map<String, Integer> majorCounts = preferences.getMajorInteractionCounts();
        if (majorCounts == null) {
            majorCounts = new HashMap<>();
        }
        majorCounts.merge(document.getMajor(), 1, Integer::sum);
        preferences.setMajorInteractionCounts(majorCounts);

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

    @Transactional(readOnly = true)
    public Map<String, Double> calculateContentTypeWeights(String userId) {
        // Get recent interactions
        Date recentDate = Date.from(Instant.now().minus(DAYS_FOR_RECENT_INTERACTIONS, ChronoUnit.DAYS));
        List<DocumentInteraction> recentInteractions =
                interactionRepository.findByUserIdAndCreatedAtAfter(userId, recentDate);

        Map<String, Map<String, Integer>> typeInteractions = new HashMap<>();

        // Count interactions by document type
        for (DocumentInteraction interaction : recentInteractions) {
            DocumentInformation doc = documentRepository.findById(interaction.getDocumentId())
                    .orElse(null);
            if (doc != null) {
                String docType = doc.getDocumentType().name();
                typeInteractions.computeIfAbsent(docType, k -> new HashMap<>());

                String intType = interaction.getInteractionType().name();
                typeInteractions.get(docType).merge(intType, 1, Integer::sum);
            }
        }

        // Calculate weighted scores
        Map<String, Double> weightedScores = new HashMap<>();

        typeInteractions.forEach((docType, interactions) -> {
            double score =
                    (interactions.getOrDefault("FAVORITE", 0) * FAVORITE_WEIGHT) +
                            (interactions.getOrDefault("COMMENT", 0) * COMMENT_WEIGHT) +
                            (interactions.getOrDefault("DOWNLOAD", 0) * DOWNLOAD_WEIGHT) +
                            (interactions.getOrDefault("VIEW", 0) * VIEW_WEIGHT);

            weightedScores.put(docType, score);
        });

        // Normalize weights
        double totalWeight = weightedScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        if (totalWeight > 0) {
            weightedScores.forEach((type, weight) ->
                    weightedScores.put(type, weight / totalWeight));
        }

        return weightedScores;
    }

    @Transactional
    public DocumentPreferences createDefaultPreferences(String userId) {
        DocumentPreferences preferences = new DocumentPreferences();
        preferences.setUserId(userId);

        // Initialize empty collections
        preferences.setPreferredMajors(new HashSet<>());
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
        preferences.setCreatedAt(new Date());
        preferences.setUpdatedAt(new Date());

        return preferencesRepository.save(preferences);
    }

    @Transactional(readOnly = true)
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
    public Map<String, Object> getInteractionStatistics(String userId) {
        Map<String, Object> stats = new HashMap<>();

        // Get all recent interactions
        List<DocumentInteraction> interactions = interactionRepository.findByUserIdAndCreatedAtAfter(
                userId,
                Date.from(Instant.now().minus(DAYS_FOR_RECENT_INTERACTIONS, ChronoUnit.DAYS))
        );

        // Count interactions by type
        Map<InteractionType, Long> interactionCounts = interactions.stream()
                .collect(Collectors.groupingBy(
                        DocumentInteraction::getInteractionType,
                        Collectors.counting()
                ));

        stats.put("interactionCounts", interactionCounts);
        stats.put("uniqueDocumentsAccessed", interactions.stream()
                .map(DocumentInteraction::getDocumentId)
                .distinct()
                .count());

        return stats;
    }
}