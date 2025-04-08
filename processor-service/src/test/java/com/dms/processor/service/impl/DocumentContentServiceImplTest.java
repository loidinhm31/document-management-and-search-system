package com.dms.processor.service.impl;

import com.dms.processor.model.DocumentContent;
import com.dms.processor.repository.DocumentContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentContentServiceImplTest {

    @Mock
    private DocumentContentRepository documentContentRepository;

    @InjectMocks
    private DocumentContentServiceImpl documentContentService;

    @Captor
    private ArgumentCaptor<DocumentContent> documentContentCaptor;

    private static final String DOCUMENT_ID = "doc-123";
    private static final Integer VERSION_NUMBER = 2;
    private static final String CONTENT = "Test document content";
    private Map<String, String> metadata;

    @BeforeEach
    void setUp() {
        metadata = new HashMap<>();
        metadata.put("author", "Test User");
        metadata.put("createdDate", "2023-01-01");
    }

    @Test
    @DisplayName("saveVersionContent should save document content with correct values")
    void saveVersionContent_ShouldSaveDocumentContent() {
        // When
        documentContentService.saveVersionContent(DOCUMENT_ID, VERSION_NUMBER, CONTENT, metadata);

        // Then
        verify(documentContentRepository).save(documentContentCaptor.capture());

        DocumentContent capturedContent = documentContentCaptor.getValue();
        assertThat(capturedContent.getId()).isEqualTo("doc-123-v2");
        assertThat(capturedContent.getDocumentId()).isEqualTo(DOCUMENT_ID);
        assertThat(capturedContent.getVersionNumber()).isEqualTo(VERSION_NUMBER);
        assertThat(capturedContent.getContent()).isEqualTo(CONTENT);
        assertThat(capturedContent.getExtractedMetadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("saveVersionContent should trim the content before saving")
    void saveVersionContent_ShouldTrimContent() {
        // Given
        String contentWithWhitespace = "  Test content with whitespace  ";

        // When
        documentContentService.saveVersionContent(DOCUMENT_ID, VERSION_NUMBER, contentWithWhitespace, metadata);

        // Then
        verify(documentContentRepository).save(documentContentCaptor.capture());

        DocumentContent capturedContent = documentContentCaptor.getValue();
        assertThat(capturedContent.getContent()).isEqualTo("Test content with whitespace");
    }

    @Test
    @DisplayName("saveVersionContent should handle empty metadata")
    void saveVersionContent_ShouldHandleEmptyMetadata() {
        // Given
        Map<String, String> emptyMetadata = new HashMap<>();

        // When
        documentContentService.saveVersionContent(DOCUMENT_ID, VERSION_NUMBER, CONTENT, emptyMetadata);

        // Then
        verify(documentContentRepository).save(documentContentCaptor.capture());

        DocumentContent capturedContent = documentContentCaptor.getValue();
        assertThat(capturedContent.getExtractedMetadata()).isEmpty();
    }

    @Test
    @DisplayName("saveVersionContent should handle null metadata")
    void saveVersionContent_ShouldHandleNullMetadata() {
        // When
        documentContentService.saveVersionContent(DOCUMENT_ID, VERSION_NUMBER, CONTENT, null);

        // Then
        verify(documentContentRepository).save(documentContentCaptor.capture());

        DocumentContent capturedContent = documentContentCaptor.getValue();
        assertThat(capturedContent.getExtractedMetadata()).isNull();
    }

    @Test
    @DisplayName("getVersionContent should return content when found")
    void getVersionContent_ShouldReturnContent_WhenFound() {
        // Given
        DocumentContent mockContent = DocumentContent.builder()
                .id(DOCUMENT_ID + "-v" + VERSION_NUMBER)
                .documentId(DOCUMENT_ID)
                .versionNumber(VERSION_NUMBER)
                .content(CONTENT)
                .extractedMetadata(metadata)
                .build();

        when(documentContentRepository.findByDocumentIdAndVersionNumber(DOCUMENT_ID, VERSION_NUMBER))
                .thenReturn(Optional.of(mockContent));

        // When
        Optional<DocumentContent> result = documentContentService.getVersionContent(DOCUMENT_ID, VERSION_NUMBER);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(mockContent);
        verify(documentContentRepository).findByDocumentIdAndVersionNumber(DOCUMENT_ID, VERSION_NUMBER);
    }

    @Test
    @DisplayName("getVersionContent should return empty when not found")
    void getVersionContent_ShouldReturnEmpty_WhenNotFound() {
        // Given
        when(documentContentRepository.findByDocumentIdAndVersionNumber(DOCUMENT_ID, VERSION_NUMBER))
                .thenReturn(Optional.empty());

        // When
        Optional<DocumentContent> result = documentContentService.getVersionContent(DOCUMENT_ID, VERSION_NUMBER);

        // Then
        assertThat(result).isEmpty();
        verify(documentContentRepository).findByDocumentIdAndVersionNumber(DOCUMENT_ID, VERSION_NUMBER);
    }

    @Test
    @DisplayName("Content ID should be correctly generated from document ID and version")
    void contentIdShouldBeCorrectlyGenerated() {
        // When
        documentContentService.saveVersionContent(DOCUMENT_ID, VERSION_NUMBER, CONTENT, metadata);

        // Then
        verify(documentContentRepository).save(documentContentCaptor.capture());

        DocumentContent capturedContent = documentContentCaptor.getValue();
        assertThat(capturedContent.getId()).isEqualTo(DOCUMENT_ID + "-v" + VERSION_NUMBER);
    }

    @Test
    @DisplayName("saveVersionContent should handle different document IDs and versions")
    void saveVersionContent_ShouldHandleDifferentIds() {
        // Given
        String altDocId = "alt-doc-456";
        Integer altVersion = 5;

        // When
        documentContentService.saveVersionContent(altDocId, altVersion, CONTENT, metadata);

        // Then
        verify(documentContentRepository).save(documentContentCaptor.capture());

        DocumentContent capturedContent = documentContentCaptor.getValue();
        assertThat(capturedContent.getId()).isEqualTo("alt-doc-456-v5");
        assertThat(capturedContent.getDocumentId()).isEqualTo(altDocId);
        assertThat(capturedContent.getVersionNumber()).isEqualTo(altVersion);
    }

    @Test
    @DisplayName("saveVersionContent should handle empty content")
    void saveVersionContent_ShouldHandleEmptyContent() {
        // Given
        String emptyContent = "";

        // When
        documentContentService.saveVersionContent(DOCUMENT_ID, VERSION_NUMBER, emptyContent, metadata);

        // Then
        verify(documentContentRepository).save(documentContentCaptor.capture());

        DocumentContent capturedContent = documentContentCaptor.getValue();
        assertThat(capturedContent.getContent()).isEmpty();
    }
}