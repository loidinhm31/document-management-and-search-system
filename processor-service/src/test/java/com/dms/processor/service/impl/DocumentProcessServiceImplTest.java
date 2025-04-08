package com.dms.processor.service.impl;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.enums.DocumentReportStatus;
import com.dms.processor.enums.DocumentStatus;
import com.dms.processor.enums.DocumentType;
import com.dms.processor.enums.EventType;
import com.dms.processor.exception.DocumentProcessingException;
import com.dms.processor.mapper.DocumentIndexMapper;
import com.dms.processor.model.DocumentContent;
import com.dms.processor.model.DocumentInformation;
import com.dms.processor.model.DocumentVersion;
import com.dms.processor.opensearch.DocumentIndex;
import com.dms.processor.opensearch.repository.DocumentIndexRepository;
import com.dms.processor.repository.DocumentRepository;
import com.dms.processor.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentProcessServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentIndexRepository documentIndexRepository;

    @Mock
    private ContentExtractorService contentExtractorService;

    @Mock
    private LanguageDetectionService languageDetectionService;

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private DocumentIndexMapper documentIndexMapper;

    @Mock
    private DocumentContentService documentContentService;

    @Mock
    private DocumentEmailService documentEmailService;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private DocumentProcessServiceImpl documentProcessService;

    private DocumentInformation document;
    private DocumentVersion documentVersion;
    private DocumentIndex documentIndex;
    private DocumentContent documentContent;
    private Path tempFile;

    @BeforeEach
    void setUp() {
        // Create a sample document
        document = DocumentInformation.builder()
                .id("doc-123")
                .filename("test-document.pdf")
                .filePath("documents/test-document.pdf")
                .documentType(DocumentType.PDF)
                .status(DocumentStatus.PENDING)
                .userId("user-123")
                .content("Initial content")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .currentVersion(1)
                .build();

        // Create a sample document version
        documentVersion = DocumentVersion.builder()
                .versionNumber(1)
                .filename("test-document.pdf")
                .filePath("documents/test-document.pdf")
                .status(DocumentStatus.PENDING)
                .documentType(DocumentType.PDF)
                .createdAt(Instant.now())
                .createdBy("user-123")
                .build();

        // Add version to document
        document.setVersions(Collections.singletonList(documentVersion));

        // Create a document index
        documentIndex = DocumentIndex.builder()
                .id("doc-123")
                .filename("test-document.pdf")
                .content("Initial content")
                .documentType(DocumentType.PDF)
                .status(DocumentStatus.PENDING)
                .build();

        // Create document content
        documentContent = DocumentContent.builder()
                .id("doc-123-v1")
                .documentId("doc-123")
                .versionNumber(1)
                .content("Extracted content")
                .extractedMetadata(new HashMap<>())
                .build();

        // Mock temp file path
        tempFile = Path.of("/tmp/test-document.pdf");
    }

    @Test
    void processDocument_withSyncEvent_shouldProcessSuccessfully() throws IOException {
        // Arrange
        DocumentExtractContent extractContent = new DocumentExtractContent("Extracted content", new HashMap<>());
        byte[] thumbnailData = "thumbnail data".getBytes();

        // Mock S3 download
        when(s3Service.downloadToTemp(document.getFilePath())).thenReturn(tempFile);

        // Mock content extraction
        when(contentExtractorService.extractContent(tempFile)).thenReturn(extractContent);

        // Mock language detection
        when(languageDetectionService.detectLanguage(anyString())).thenReturn(Optional.of("en"));

        // Mock thumbnail generation
        when(thumbnailService.generateThumbnail(eq(tempFile), eq(document.getDocumentType()), anyString()))
                .thenReturn(thumbnailData);

        // Mock S3 upload for thumbnail
        when(s3Service.uploadFile(any(Path.class), eq("thumbnails"), eq("image/png")))
                .thenReturn("thumbnails/123-thumbnail.png");

        // Mock document index mapper
        when(documentIndexMapper.toDocumentIndex(any(DocumentInformation.class))).thenReturn(documentIndex);

        // Act
        documentProcessService.processDocument(document, 1, EventType.SYNC_EVENT);

        // Assert
        verify(s3Service).downloadToTemp(document.getFilePath());
        verify(contentExtractorService).extractContent(tempFile);
        verify(languageDetectionService).detectLanguage("Extracted content");
        verify(documentContentService).saveVersionContent(
                document.getId(), documentVersion.getVersionNumber(), extractContent.content(), extractContent.metadata());
        verify(thumbnailService).generateThumbnail(tempFile, document.getDocumentType(), document.getContent());
        // The method is called once when setting to PROCESSING, and once when setting to COMPLETED
        verify(documentIndexMapper, times(2)).toDocumentIndex(document);
        verify(documentIndexRepository, times(2)).save(documentIndex);
        verify(s3Service).cleanup(tempFile);

        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        assertEquals("Extracted content", document.getContent());
        assertEquals("en", document.getLanguage());
        assertNotNull(document.getThumbnailPath());
    }

    @Test
    void processDocument_withUpdateEvent_shouldUpdateMetadataOnly() throws IOException {
        // Arrange
        when(documentIndexMapper.toDocumentIndex(any(DocumentInformation.class))).thenReturn(documentIndex);

        // Act
        documentProcessService.processDocument(document, 1, EventType.UPDATE_EVENT);

        // Assert
        // The method is called once when setting to PROCESSING, and once when setting to COMPLETED
        verify(documentIndexMapper, times(2)).toDocumentIndex(document);
        verify(documentIndexRepository, times(2)).save(documentIndex);
        verify(s3Service, never()).downloadToTemp(anyString());
        verify(contentExtractorService, never()).extractContent(any(Path.class));

        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
    }

    @Test
    void processDocument_withRevertEvent_shouldRevertToVersion() {
        // Arrange
        Integer revertToVersion = 1;

        when(documentContentService.getVersionContent(document.getId(), revertToVersion))
                .thenReturn(Optional.of(documentContent));
        when(documentIndexMapper.toDocumentIndex(any(DocumentInformation.class))).thenReturn(documentIndex);

        // Act
        documentProcessService.processDocument(document, revertToVersion, EventType.REVERT_EVENT);

        // Assert
        verify(documentContentService).getVersionContent(document.getId(), revertToVersion);
        verify(documentContentService).saveVersionContent(
                document.getId(), document.getCurrentVersion(), documentContent.getContent(), documentContent.getExtractedMetadata());
        // The method is called once when setting to PROCESSING, and once when setting to COMPLETED
        verify(documentIndexMapper, times(2)).toDocumentIndex(document);
        verify(documentIndexRepository, times(2)).save(documentIndex);

        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        assertEquals(documentContent.getContent(), document.getContent());
    }

    @Test
    void processDocument_withUpdateWithFileEvent_shouldProcess() throws IOException {
        // Arrange
        DocumentExtractContent extractContent = new DocumentExtractContent("Extracted content", new HashMap<>());
        byte[] thumbnailData = "thumbnail data".getBytes();

        // Mock S3 download
        when(s3Service.downloadToTemp(document.getFilePath())).thenReturn(tempFile);

        // Mock content extraction
        when(contentExtractorService.extractContent(tempFile)).thenReturn(extractContent);

        // Mock language detection
        when(languageDetectionService.detectLanguage(anyString())).thenReturn(Optional.of("en"));

        // Mock thumbnail generation
        when(thumbnailService.generateThumbnail(eq(tempFile), eq(document.getDocumentType()), anyString()))
                .thenReturn(thumbnailData);

        // Mock S3 upload for thumbnail
        when(s3Service.uploadFile(any(Path.class), eq("thumbnails"), eq("image/png")))
                .thenReturn("thumbnails/123-thumbnail.png");

        // Mock document index mapper
        when(documentIndexMapper.toDocumentIndex(any(DocumentInformation.class))).thenReturn(documentIndex);

        // Act
        documentProcessService.processDocument(document, 1, EventType.UPDATE_EVENT_WITH_FILE);

        // Assert
        verify(s3Service).downloadToTemp(document.getFilePath());
        verify(contentExtractorService).extractContent(tempFile);
        verify(languageDetectionService).detectLanguage("Extracted content");
        verify(documentContentService).saveVersionContent(
                document.getId(), documentVersion.getVersionNumber(), extractContent.content(), extractContent.metadata());
        verify(thumbnailService).generateThumbnail(tempFile, document.getDocumentType(), document.getContent());
        // The method is called once when setting to PROCESSING, and once when setting to COMPLETED
        verify(documentIndexMapper, times(2)).toDocumentIndex(document);
        verify(documentIndexRepository, times(2)).save(documentIndex);
        verify(s3Service).cleanup(tempFile);

        assertEquals(DocumentStatus.COMPLETED, document.getStatus());
        assertEquals("Extracted content", document.getContent());
    }

    @Test
    void processDocument_whenExtractionFails_shouldHandleError() throws IOException {
        // Arrange
        when(s3Service.downloadToTemp(document.getFilePath())).thenReturn(tempFile);
        // Using RuntimeException instead of IOException since we're mocking an interface method
        // that doesn't declare checked exceptions
        when(contentExtractorService.extractContent(tempFile))
                .thenThrow(new RuntimeException("Extraction failed"));
        when(documentIndexMapper.toDocumentIndex(any(DocumentInformation.class))).thenReturn(documentIndex);

        // Act & Assert
        assertThrows(DocumentProcessingException.class, () ->
                documentProcessService.processDocument(document, 1, EventType.SYNC_EVENT)
        );

        verify(s3Service).downloadToTemp(document.getFilePath());
        verify(contentExtractorService).extractContent(tempFile);
        // The method is called once when setting to PROCESSING, and once when setting to FAILED
        verify(documentIndexMapper, times(2)).toDocumentIndex(document);
        verify(documentIndexRepository, times(2)).save(documentIndex);
        verify(s3Service).cleanup(tempFile);

        assertEquals(DocumentStatus.FAILED, document.getStatus());
        assertNotNull(document.getProcessingError());
    }

    @Test
    void handleReportStatus_whenReportRejected_shouldSendRejectionNotifications() {
        // Arrange
        String userId = "admin-123";
        int times = 1;
        document.setReportStatus(DocumentReportStatus.REJECTED);

        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentIndexMapper.toDocumentIndex(document)).thenReturn(documentIndex);

        // Act
        documentProcessService.handleReportStatus(document.getId(), userId, times);

        // Assert
        verify(documentRepository).findById(document.getId());
        verify(documentIndexMapper).toDocumentIndex(document);
        verify(documentIndexRepository).save(documentIndex);
        verify(documentEmailService).sendDocumentReportRejectionNotifications(document, userId, times);
    }

    @Test
    void handleReportStatus_whenReportResolved_shouldSendResolveNotifications() {
        // Arrange
        String userId = "admin-123";
        int times = 1;
        document.setReportStatus(DocumentReportStatus.RESOLVED);

        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentIndexMapper.toDocumentIndex(document)).thenReturn(documentIndex);

        // Act
        documentProcessService.handleReportStatus(document.getId(), userId, times);

        // Assert
        verify(documentRepository).findById(document.getId());
        verify(documentIndexMapper).toDocumentIndex(document);
        verify(documentIndexRepository).save(documentIndex);
        verify(documentEmailService).sendResolveNotifications(document, userId, times);
    }

    @Test
    void handleReportStatus_whenReportRemediated_shouldSendRemediationNotifications() {
        // Arrange
        String userId = "admin-123";
        int times = 1;
        document.setReportStatus(DocumentReportStatus.REMEDIATED);

        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentIndexMapper.toDocumentIndex(document)).thenReturn(documentIndex);

        // Act
        documentProcessService.handleReportStatus(document.getId(), userId, times);

        // Assert
        verify(documentRepository).findById(document.getId());
        verify(documentIndexMapper).toDocumentIndex(document);
        verify(documentIndexRepository).save(documentIndex);
        verify(documentEmailService).sendReportRemediationNotifications(document, userId);
    }

    @Test
    void handleReportStatus_whenDocumentNotFound_shouldThrowException() {
        // Arrange
        String documentId = "non-existent-doc";
        String userId = "admin-123";
        int times = 1;

        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(DocumentProcessingException.class, () ->
                documentProcessService.handleReportStatus(documentId, userId, times)
        );

        verify(documentRepository).findById(documentId);
        verify(documentEmailService, never()).sendDocumentReportRejectionNotifications(any(), any(), anyInt());
        verify(documentEmailService, never()).sendResolveNotifications(any(), any(), anyInt());
        verify(documentEmailService, never()).sendReportRemediationNotifications(any(), any());
    }

    @Test
    void deleteDocumentFromIndex_shouldDeleteDocumentFromIndex() {
        // Arrange
        String documentId = "doc-123";

        // Act
        documentProcessService.deleteDocumentFromIndex(documentId);

        // Assert
        verify(documentIndexRepository).deleteById(documentId);
    }

    @Test
    void deleteDocumentFromIndex_whenDeleteFails_shouldThrowException() {
        // Arrange
        String documentId = "doc-123";
        doThrow(new RuntimeException("Delete failed")).when(documentIndexRepository).deleteById(documentId);

        // Act & Assert
        assertThrows(DocumentProcessingException.class, () ->
                documentProcessService.deleteDocumentFromIndex(documentId)
        );

        verify(documentIndexRepository).deleteById(documentId);
    }

    @Test
    void processDocument_withNoVersionNumber_shouldThrowException() {
        // Arrange
        document.setCurrentVersion(null);

        // Act & Assert
        assertThrows(DocumentProcessingException.class, () ->
                documentProcessService.processDocument(document, null, EventType.SYNC_EVENT)
        );
    }

    @Test
    void processDocument_withVersionNotFound_shouldThrowException() {
        // Arrange
        document.setVersions(new ArrayList<>());

        // Act & Assert
        assertThrows(DocumentProcessingException.class, () ->
                documentProcessService.processDocument(document, 1, EventType.SYNC_EVENT)
        );
    }

    @Test
    void processDocument_withRevertEvent_whenContentNotFound_shouldThrowException() {
        // Arrange
        Integer revertToVersion = 1;

        when(documentContentService.getVersionContent(document.getId(), revertToVersion))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(DocumentProcessingException.class, () ->
                documentProcessService.processDocument(document, revertToVersion, EventType.REVERT_EVENT)
        );

        verify(documentContentService).getVersionContent(document.getId(), revertToVersion);
    }

    @Test
    void processDocument_withUnsupportedEventType_shouldThrowException() {
        // The DocumentProcessServiceImpl wraps the IllegalArgumentException in a DocumentProcessingException
        // so we need to check for the root cause
        DocumentProcessingException exception = assertThrows(DocumentProcessingException.class, () ->
                documentProcessService.processDocument(document, 1, EventType.FAVORITE_NOTIFICATION)
        );

        // Verify that the root cause is an IllegalArgumentException
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("Unsupported event type: FAVORITE_NOTIFICATION", exception.getCause().getMessage());
    }
}