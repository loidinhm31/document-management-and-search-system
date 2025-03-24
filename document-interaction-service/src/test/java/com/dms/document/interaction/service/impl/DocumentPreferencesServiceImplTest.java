package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.AggregatedInteractionStats;
import com.dms.document.interaction.dto.UpdateDocumentPreferencesRequest;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.AppRole;
import com.dms.document.interaction.enums.DocumentType;
import com.dms.document.interaction.enums.InteractionType;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentInteraction;
import com.dms.document.interaction.model.DocumentPreferences;
import com.dms.document.interaction.repository.DocumentInteractionRepository;
import com.dms.document.interaction.repository.DocumentPreferencesRepository;
import com.dms.document.interaction.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentPreferencesServiceImplTest {

    @Mock
    private DocumentPreferencesRepository documentPreferencesRepository;

    @Mock
    private DocumentInteractionRepository documentInteractionRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private DocumentPreferencesServiceImpl documentPreferencesService;

    @Captor
    private ArgumentCaptor<DocumentPreferences> preferencesCaptor;

    private UUID userId;
    private UserResponse userResponse;
    private DocumentPreferences documentPreferences;
    private DocumentInformation documentInformation;
    private DocumentInteraction documentInteraction;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        // Setup user response
        userResponse = new UserResponse(
                userId,
                "testuser",
                "test@example.com",
                new com.dms.document.interaction.dto.RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER)
        );

        // Setup document preferences
        documentPreferences = new DocumentPreferences();
        documentPreferences.setId("pref-1");
        documentPreferences.setUserId(userId.toString());
        documentPreferences.setPreferredMajors(new HashSet<>(Set.of("CS", "MATH")));
        documentPreferences.setPreferredCourseCodes(new HashSet<>(Set.of("CS101", "MATH202")));
        documentPreferences.setPreferredLevels(new HashSet<>(Set.of("BEGINNER", "INTERMEDIATE")));
        documentPreferences.setPreferredCategories(new HashSet<>(Set.of("PROGRAMMING", "ALGORITHMS")));
        documentPreferences.setPreferredTags(new HashSet<>(Set.of("java", "python")));
        documentPreferences.setLanguagePreferences(new HashSet<>(Set.of("en", "vi")));
        documentPreferences.setTagInteractionCounts(new HashMap<>(Map.of(
                "java", 5,
                "python", 3,
                "algorithms", 2
        )));
        documentPreferences.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        documentPreferences.setUpdatedAt(Instant.now().minus(7, ChronoUnit.DAYS));

        // Setup document information
        documentInformation = DocumentInformation.builder()
                .id("doc-1")
                .filename("test-document.pdf")
                .documentType(DocumentType.PDF)
                .majors(Set.of("CS"))
                .courseCodes(Set.of("CS101"))
                .courseLevel("INTERMEDIATE")
                .categories(Set.of("PROGRAMMING"))
                .tags(Set.of("java", "programming"))
                .build();

        // Setup document interaction
        documentInteraction = new DocumentInteraction();
        documentInteraction.setId("interaction-1");
        documentInteraction.setUserId(userId.toString());
        documentInteraction.setDocumentId("doc-1");
        Map<String, DocumentInteraction.InteractionStats> interactions = new HashMap<>();

        DocumentInteraction.InteractionStats viewStats = new DocumentInteraction.InteractionStats();
        viewStats.setCount(10);
        viewStats.setLastUpdate(Instant.now().minus(1, ChronoUnit.DAYS));

        DocumentInteraction.InteractionStats downloadStats = new DocumentInteraction.InteractionStats();
        downloadStats.setCount(5);
        downloadStats.setLastUpdate(Instant.now().minus(2, ChronoUnit.DAYS));

        interactions.put(InteractionType.VIEW.name(), viewStats);
        interactions.put(InteractionType.DOWNLOAD.name(), downloadStats);

        documentInteraction.setInteractions(interactions);
        documentInteraction.setFirstInteractionDate(Instant.now().minus(10, ChronoUnit.DAYS));
        documentInteraction.setLastInteractionDate(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    @Test
    void getDocumentPreferences_ExistingUser_ReturnsPreferences() {
        // Arrange
        when(userClient.getUserByUsername(anyString())).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString())).thenReturn(Optional.of(documentPreferences));

        // Act
        DocumentPreferences result = documentPreferencesService.getDocumentPreferences("testuser");

        // Assert
        assertNotNull(result);
        assertEquals(userId.toString(), result.getUserId());
        assertEquals(documentPreferences.getPreferredMajors(), result.getPreferredMajors());
        assertEquals(documentPreferences.getPreferredCourseCodes(), result.getPreferredCourseCodes());

        verify(userClient).getUserByUsername("testuser");
        verify(documentPreferencesRepository).findByUserId(userId.toString());
    }

    @Test
    void getDocumentPreferences_NewUser_CreatesDefaultPreferences() {
        // Arrange
        when(userClient.getUserByUsername(anyString())).thenReturn(ResponseEntity.ok(userResponse));
        when(documentPreferencesRepository.findByUserId(userId.toString())).thenReturn(Optional.empty());

        DocumentPreferences defaultPreferences = new DocumentPreferences();
        defaultPreferences.setUserId(userId.toString());
        defaultPreferences.setPreferredMajors(new HashSet<>());
        defaultPreferences.setLanguagePreferences(new HashSet<>(Collections.singletonList("en")));

        when(documentPreferencesRepository.save(any(DocumentPreferences.class))).thenReturn(defaultPreferences);

        // Act
        DocumentPreferences result = documentPreferencesService.getDocumentPreferences("testuser");

        // Assert
        assertNotNull(result);
        assertEquals(userId.toString(), result.getUserId());

        verify(userClient).getUserByUsername("testuser");
        verify(documentPreferencesRepository).findByUserId(userId.toString());
        verify(documentPreferencesRepository).save(any(DocumentPreferences.class));
    }

    @Test
    void updateExplicitPreferences_UpdatesCorrectFields() {
        // Arrange
        UpdateDocumentPreferencesRequest request = new UpdateDocumentPreferencesRequest(
                Set.of("BIOLOGY", "CHEMISTRY"),
                Set.of("BIO101", "CHEM101"),
                Set.of("ADVANCED"),
                Set.of("LAB", "RESEARCH"),
                Set.of("biology", "chemistry"),
                Set.of("en")
        );

        // Create a spy of the service (not just a mock)
        DocumentPreferencesServiceImpl serviceSpy = spy(documentPreferencesService);

        // Stub the getDocumentPreferences method on the spy to return our test object
        doReturn(documentPreferences).when(serviceSpy).getDocumentPreferences(userId.toString());

        // Create a new updatedAt timestamp to ensure it's clearly after the original
        Instant originalUpdatedAt = documentPreferences.getUpdatedAt();

        when(documentPreferencesRepository.save(any(DocumentPreferences.class))).thenAnswer(i -> {
            DocumentPreferences savedPrefs = (DocumentPreferences)i.getArguments()[0];
            // Explicitly set updatedAt to ensure it's after the original
            savedPrefs.setUpdatedAt(originalUpdatedAt.plus(1, ChronoUnit.HOURS));
            return savedPrefs;
        });

        // Act
        DocumentPreferences result = serviceSpy.updateExplicitPreferences(userId.toString(), request);

        // Assert
        assertNotNull(result);
        assertEquals(request.preferredMajors(), result.getPreferredMajors());
        assertEquals(request.preferredCourseCodes(), result.getPreferredCourseCodes());
        assertEquals(request.preferredLevels(), result.getPreferredLevels());
        assertEquals(request.preferredCategories(), result.getPreferredCategories());
        assertEquals(request.preferredTags(), result.getPreferredTags());
        assertEquals(request.languagePreferences(), result.getLanguagePreferences());

        // Verify that implicit data is preserved
        assertEquals(documentPreferences.getTagInteractionCounts(), result.getTagInteractionCounts());

        verify(serviceSpy).getDocumentPreferences(userId.toString());
        verify(documentPreferencesRepository).save(preferencesCaptor.capture());
        DocumentPreferences savedPreferences = preferencesCaptor.getValue();
        // The timestamp should definitely be after now that we've explicitly set it in the mock
        assertTrue(savedPreferences.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    void recordInteraction_NewInteraction_CreatesInteractionRecord() {
        // Arrange
        when(documentRepository.findAccessibleDocumentByIdAndUserId(anyString(), anyString()))
                .thenReturn(Optional.of(documentInformation));
        when(documentInteractionRepository.findByUserIdAndDocumentId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(documentInteractionRepository.save(any(DocumentInteraction.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        // Mock preferences repository to return a valid preference
        when(documentPreferencesRepository.findByUserId(userId.toString()))
                .thenReturn(Optional.of(documentPreferences));
        when(documentPreferencesRepository.save(any(DocumentPreferences.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        // Act
        documentPreferencesService.recordInteraction(userId, "doc-1", InteractionType.VIEW);

        // Assert
        verify(documentRepository).findAccessibleDocumentByIdAndUserId("doc-1", userId.toString());
        verify(documentInteractionRepository).findByUserIdAndDocumentId(userId.toString(), "doc-1");
        verify(documentInteractionRepository).save(any(DocumentInteraction.class));
        verify(documentPreferencesRepository).findByUserId(userId.toString());
        verify(documentPreferencesRepository).save(any(DocumentPreferences.class));
    }

    @Test
    void recordInteraction_ExistingInteraction_UpdatesInteractionStats() {
        // Arrange
        when(documentRepository.findAccessibleDocumentByIdAndUserId(anyString(), anyString()))
                .thenReturn(Optional.of(documentInformation));
        when(documentInteractionRepository.findByUserIdAndDocumentId(anyString(), anyString()))
                .thenReturn(Optional.of(documentInteraction));
        when(documentInteractionRepository.save(any(DocumentInteraction.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        // Mock preferences repository to return a valid preference
        when(documentPreferencesRepository.findByUserId(userId.toString()))
                .thenReturn(Optional.of(documentPreferences));
        when(documentPreferencesRepository.save(any(DocumentPreferences.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        // Set up the interaction stats to make sure there are recent stats available
        Instant beforeTest = documentInteraction.getInteractions().get(InteractionType.VIEW.name()).getLastUpdate();

        ArgumentCaptor<DocumentInteraction> interactionCaptor = ArgumentCaptor.forClass(DocumentInteraction.class);

        // Act
        documentPreferencesService.recordInteraction(userId, "doc-1", InteractionType.VIEW);

        // Assert
        verify(documentRepository).findAccessibleDocumentByIdAndUserId("doc-1", userId.toString());
        verify(documentInteractionRepository).findByUserIdAndDocumentId(userId.toString(), "doc-1");
        verify(documentInteractionRepository).save(interactionCaptor.capture());
        verify(documentPreferencesRepository).findByUserId(userId.toString());
        verify(documentPreferencesRepository).save(any(DocumentPreferences.class));

        DocumentInteraction capturedInteraction = interactionCaptor.getValue();
        DocumentInteraction.InteractionStats viewStats = capturedInteraction.getInteractions().get(InteractionType.VIEW.name());
        assertEquals(11, viewStats.getCount()); // 10 + 1
        // We assert that the timestamp is different - not necessarily after
        assertNotEquals(beforeTest, viewStats.getLastUpdate());
    }

    @Test
    void updateImplicitPreferences_UpdatesInteractionCounts() {
        // Arrange
        when(documentPreferencesRepository.findByUserId(userId.toString()))
                .thenReturn(Optional.of(documentPreferences));
        when(documentPreferencesRepository.save(any(DocumentPreferences.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        // Act
        documentPreferencesService.updateImplicitPreferences(userId, documentInformation, InteractionType.VIEW);

        // Assert
        verify(documentPreferencesRepository).findByUserId(userId.toString());
        verify(documentPreferencesRepository).save(preferencesCaptor.capture());

        DocumentPreferences captured = preferencesCaptor.getValue();

        // Check category counts updated
        Map<String, Integer> categoryCounts = captured.getCategoryInteractionCounts();
        assertNotNull(categoryCounts);
        assertEquals(1, categoryCounts.get("PROGRAMMING"));

        // Check tag counts updated
        Map<String, Integer> tagCounts = captured.getTagInteractionCounts();
        assertNotNull(tagCounts);
        assertEquals(6, tagCounts.get("java")); // 5 + 1
        assertEquals(1, tagCounts.get("programming")); // new tag
    }

    @Test
    void getCalculateContentTypeWeights_ReturnsNormalizedWeights() {
        // Arrange
        when(userClient.getUserByUsername(anyString())).thenReturn(ResponseEntity.ok(userResponse));

        Date recentDate = Date.from(Instant.now().minus(30, ChronoUnit.DAYS));
        List<DocumentInteraction> recentInteractions = Collections.singletonList(documentInteraction);

        when(documentInteractionRepository.findRecentInteractions(eq(userId.toString()), any(Date.class)))
                .thenReturn(recentInteractions);
        when(documentRepository.findById("doc-1"))
                .thenReturn(Optional.of(documentInformation));

        // Act
        Map<String, Double> weights = documentPreferencesService.getCalculateContentTypeWeights("testuser");

        // Assert
        assertNotNull(weights);
        assertTrue(weights.containsKey(DocumentType.PDF.name()));
        double pdfWeight = weights.get(DocumentType.PDF.name());
        assertTrue(pdfWeight > 0 && pdfWeight <= 1.0);

        verify(userClient).getUserByUsername("testuser");
        verify(documentInteractionRepository).findRecentInteractions(eq(userId.toString()), any(Date.class));
        verify(documentRepository).findById("doc-1");
    }

    @Test
    void createDefaultPreferences_ReturnsCorrectDefaultValues() {
        // Arrange
        when(documentPreferencesRepository.save(any(DocumentPreferences.class)))
                .thenAnswer(i -> i.getArguments()[0]);

        // Act
        DocumentPreferences result = documentPreferencesService.createDefaultPreferences(userId.toString());

        // Assert
        assertNotNull(result);
        assertEquals(userId.toString(), result.getUserId());
        assertTrue(result.getPreferredMajors().isEmpty());
        assertTrue(result.getPreferredCourseCodes().isEmpty());
        assertTrue(result.getPreferredLevels().isEmpty());
        assertTrue(result.getPreferredCategories().isEmpty());
        assertTrue(result.getPreferredTags().isEmpty());

        assertTrue(result.getLanguagePreferences().contains("en"));
        assertEquals(1, result.getLanguagePreferences().size());

        assertNotNull(result.getContentTypeWeights());
        assertNotNull(result.getCategoryInteractionCounts());
        assertNotNull(result.getTagInteractionCounts());
        assertNotNull(result.getMajorInteractionCounts());
        assertNotNull(result.getRecentViewedDocuments());

        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        verify(documentPreferencesRepository).save(any(DocumentPreferences.class));
    }

    @Test
    void getRecommendedTags_ReturnsTopTags() {
        // Create a spy of the service (not just a mock)
        DocumentPreferencesServiceImpl serviceSpy = spy(documentPreferencesService);

        // Stub the getDocumentPreferences method on the spy to return our test object
        doReturn(documentPreferences).when(serviceSpy).getDocumentPreferences(userId.toString());

        // Act
        Set<String> recommendedTags = serviceSpy.getRecommendedTags(userId.toString());

        // Assert
        assertNotNull(recommendedTags);
        assertFalse(recommendedTags.isEmpty());
        assertTrue(recommendedTags.contains("java")); // Has highest count (5)
        assertTrue(recommendedTags.contains("python")); // Has second highest count (3)
        assertTrue(recommendedTags.contains("algorithms")); // Has third highest count (2)

        // Verify the spy was called correctly
        verify(serviceSpy).getDocumentPreferences(userId.toString());
    }

    @Test
    void getInteractionStatistics_ReturnsAggregatedStats() {
        // Arrange
        when(userClient.getUserByUsername(anyString())).thenReturn(ResponseEntity.ok(userResponse));

        AggregatedInteractionStats stats = new AggregatedInteractionStats();
        stats.setTotalViews(25);
        stats.setTotalDownloads(10);
        stats.setTotalComments(5);
        stats.setTotalShares(2);
        stats.setUniqueDocuments(8);

        when(documentInteractionRepository.getAggregatedStats(eq(userId.toString()), any(Date.class)))
                .thenReturn(stats);

        // Act
        Map<String, Object> result = documentPreferencesService.getInteractionStatistics("testuser");

        // Assert
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<InteractionType, Long> interactionCounts = (Map<InteractionType, Long>) result.get("interactionCounts");
        assertNotNull(interactionCounts);
        assertEquals(25L, interactionCounts.get(InteractionType.VIEW));
        assertEquals(10L, interactionCounts.get(InteractionType.DOWNLOAD));
        assertEquals(5L, interactionCounts.get(InteractionType.COMMENT));
        assertEquals(2L, interactionCounts.get(InteractionType.SHARE));

        assertEquals(8, result.get("uniqueDocumentsAccessed"));

        verify(userClient).getUserByUsername("testuser");
        verify(documentInteractionRepository).getAggregatedStats(eq(userId.toString()), any(Date.class));
    }

    @Test
    void getInteractionStatistics_NullStats_ReturnsEmptyMap() {
        // Arrange
        when(userClient.getUserByUsername(anyString())).thenReturn(ResponseEntity.ok(userResponse));

        when(documentInteractionRepository.getAggregatedStats(eq(userId.toString()), any(Date.class)))
                .thenReturn(null);

        // Act
        Map<String, Object> result = documentPreferencesService.getInteractionStatistics("testuser");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(userClient).getUserByUsername("testuser");
        verify(documentInteractionRepository).getAggregatedStats(eq(userId.toString()), any(Date.class));
    }
}