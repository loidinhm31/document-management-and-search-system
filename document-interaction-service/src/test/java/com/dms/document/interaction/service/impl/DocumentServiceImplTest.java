package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.client.UserClient;
import com.dms.document.interaction.dto.DocumentUpdateRequest;
import com.dms.document.interaction.dto.ThumbnailResponse;
import com.dms.document.interaction.dto.UserResponse;
import com.dms.document.interaction.enums.*;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.exception.UnsupportedDocumentTypeException;
import com.dms.document.interaction.model.DocumentInformation;
import com.dms.document.interaction.model.DocumentUserHistory;
import com.dms.document.interaction.model.DocumentVersion;
import com.dms.document.interaction.repository.DocumentRepository;
import com.dms.document.interaction.repository.DocumentUserHistoryRepository;
import com.dms.document.interaction.service.DocumentNotificationService;
import com.dms.document.interaction.service.DocumentPreferencesService;
import com.dms.document.interaction.service.PublishEventService;
import com.dms.document.interaction.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentUserHistoryRepository documentUserHistoryRepository;

    @Mock
    private DocumentNotificationService documentNotificationService;

    @Mock
    private S3Service s3Service;

    @Mock
    private PublishEventService publishEventService;

    @Mock
    private DocumentPreferencesService documentPreferencesService;

    @Mock
    private UserClient userClient;

    @Mock
    private MultipartFile multipartFile;

    @Mock
    private Resource processingPlaceholder;

    @Mock
    private Resource errorPlaceholder;

    @InjectMocks
    private DocumentServiceImpl documentService;

    private final String TEST_USERNAME = "testuser";
    private final UUID TEST_USER_ID = UUID.randomUUID();
    private final String TEST_DOCUMENT_ID = "doc123";
    private final String TEST_S3_KEY = "documents/2025/03/25/testfile.pdf";
    private UserResponse testUserResponse;
    private DocumentInformation testDocumentInfo;
    private DocumentVersion testDocumentVersion;

    @BeforeEach
    void setUp() {
        // Mock property values set by @Value annotation
        ReflectionTestUtils.setField(documentService, "maxFileSize", DataSize.ofMegabytes(50));
        ReflectionTestUtils.setField(documentService, "processingPlaceholder", processingPlaceholder);
        ReflectionTestUtils.setField(documentService, "errorPlaceholder", errorPlaceholder);

        // Setup test user response
        testUserResponse = new UserResponse(
                TEST_USER_ID,
                TEST_USERNAME,
                "test@example.com",
                new com.dms.document.interaction.dto.RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER)
        );

        // Setup test document version
        testDocumentVersion = DocumentVersion.builder()
                .versionNumber(0)
                .filePath(TEST_S3_KEY)
                .filename("testfile.pdf")
                .fileSize(1024L)
                .mimeType("application/pdf")
                .documentType(DocumentType.PDF)
                .status(DocumentStatus.COMPLETED)
                .language("en")
                .createdBy(TEST_USERNAME)
                .createdAt(Instant.now())
                .build();

        // Setup test document information
        testDocumentInfo = DocumentInformation.builder()
                .id(TEST_DOCUMENT_ID)
                .status(DocumentStatus.COMPLETED)
                .filename("testfile.pdf")
                .filePath(TEST_S3_KEY)
                .thumbnailPath("thumbnails/testfile.png")
                .fileSize(1024L)
                .mimeType("application/pdf")
                .documentType(DocumentType.PDF)
                .summary("Test document")
                .majors(new HashSet<>(List.of("CS")))
                .courseCodes(new HashSet<>(List.of("CS101")))
                .courseLevel("UG")
                .categories(new HashSet<>(List.of("Programming")))
                .tags(new HashSet<>(List.of("java", "programming")))
                .userId(TEST_USER_ID.toString())
                .sharingType(SharingType.PRIVATE)
                .sharedWith(new HashSet<>())
                .deleted(false)
                .currentVersion(0)
                .versions(List.of(testDocumentVersion))
                .language("en")
                .createdAt(Instant.now())
                .createdBy(TEST_USERNAME)
                .updatedAt(Instant.now())
                .updatedBy(TEST_USERNAME)
                .recommendationCount(0)
                .build();
    }

    @Test
    void uploadDocument_Success() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getOriginalFilename()).thenReturn("testfile.pdf");
        when(s3Service.uploadFile(any(MultipartFile.class), eq("documents"))).thenReturn(TEST_S3_KEY);
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(testDocumentInfo);

        Set<String> majors = new HashSet<>(List.of("CS"));
        Set<String> courseCodes = new HashSet<>(List.of("CS101"));
        Set<String> categories = new HashSet<>(List.of("Programming"));
        Set<String> tags = new HashSet<>(List.of("java", "programming"));

        // Act
        DocumentInformation result = documentService.uploadDocument(
                multipartFile, "Test document", courseCodes, majors, "UG", categories, tags, TEST_USERNAME
        );

        // Assert
        assertNotNull(result);
        assertEquals(TEST_DOCUMENT_ID, result.getId());
        verify(userClient).getUserByUsername(TEST_USERNAME);
        verify(s3Service).uploadFile(any(MultipartFile.class), eq("documents"));
        verify(documentRepository).save(any(DocumentInformation.class));
    }

    @Test
    void uploadDocument_EmptyFile_ThrowsException() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(multipartFile.isEmpty()).thenReturn(true);

        // Act & Assert
        InvalidDocumentException exception = assertThrows(
                InvalidDocumentException.class,
                () -> documentService.uploadDocument(
                        multipartFile, "Test document", null, null, "UG", null, null, TEST_USERNAME
                )
        );
        assertEquals("File is empty", exception.getMessage());
    }

    @Test
    void uploadDocument_FileTooLarge_ThrowsException() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(DataSize.ofMegabytes(100).toBytes()); // Exceeds 50MB limit

        // Act & Assert
        InvalidDocumentException exception = assertThrows(
                InvalidDocumentException.class,
                () -> documentService.uploadDocument(
                        multipartFile, "Test document", null, null, "UG", null, null, TEST_USERNAME
                )
        );
        assertTrue(exception.getMessage().contains("File size exceeds maximum limit"));
    }

    @Test
    void uploadDocument_UnsupportedMimeType_ThrowsException() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.getContentType()).thenReturn("application/unsupported");

        // Act & Assert
        UnsupportedDocumentTypeException exception = assertThrows(
                UnsupportedDocumentTypeException.class,
                () -> documentService.uploadDocument(
                        multipartFile, "Test document", null, null, "UG", null, null, TEST_USERNAME
                )
        );
        assertTrue(exception.getMessage().contains("Unsupported document type"));
    }

    @Test
    void getDocumentThumbnail_CompletedDocument_Success() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));
        byte[] thumbnailBytes = "test thumbnail data".getBytes();
        when(s3Service.downloadFile(testDocumentInfo.getThumbnailPath())).thenReturn(thumbnailBytes);

        // Act
        ThumbnailResponse response = documentService.getDocumentThumbnail(TEST_DOCUMENT_ID, TEST_USERNAME);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertFalse(response.isPlaceholder());
        assertArrayEquals(thumbnailBytes, response.getData());
    }

    @Test
    void getDocumentThumbnail_PendingDocument_ReturnsProcessingPlaceholder() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        DocumentInformation pendingDoc = testDocumentInfo.withContent(null);
        pendingDoc.setStatus(DocumentStatus.PENDING);

        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(pendingDoc));

        byte[] placeholderBytes = "processing placeholder".getBytes();
        ByteArrayResource placeholderResource = new ByteArrayResource(placeholderBytes);
        when(processingPlaceholder.getInputStream()).thenReturn(placeholderResource.getInputStream());

        // Act
        ThumbnailResponse response = documentService.getDocumentThumbnail(TEST_DOCUMENT_ID, TEST_USERNAME);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        assertTrue(response.isPlaceholder());
        assertEquals(Integer.valueOf(10), response.getRetryAfterSeconds());
        assertArrayEquals(placeholderBytes, response.getData());
    }

    @Test
    void getDocumentThumbnail_FailedDocument_ReturnsErrorPlaceholder() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        DocumentInformation failedDoc = testDocumentInfo.withContent(null);
        failedDoc.setStatus(DocumentStatus.FAILED);

        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(failedDoc));

        byte[] placeholderBytes = "error placeholder".getBytes();
        ByteArrayResource placeholderResource = new ByteArrayResource(placeholderBytes);
        when(errorPlaceholder.getInputStream()).thenReturn(placeholderResource.getInputStream());

        // Act
        ThumbnailResponse response = documentService.getDocumentThumbnail(TEST_DOCUMENT_ID, TEST_USERNAME);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertTrue(response.isPlaceholder());
        assertArrayEquals(placeholderBytes, response.getData());
    }

    @Test
    void getDocumentContent_Success() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));
        byte[] fileContent = "test file content".getBytes();
        when(s3Service.downloadFile(testDocumentInfo.getFilePath())).thenReturn(fileContent);

        // Act
        byte[] result = documentService.getDocumentContent(TEST_DOCUMENT_ID, TEST_USERNAME, "view", false);

        // Assert
        assertNotNull(result);
        assertArrayEquals(fileContent, result);
        verify(documentPreferencesService, never()).recordInteraction(any(), any(), any());
    }

    @Test
    void getDocumentContent_WithDownloadAndHistory_RecordsInteraction() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));
        byte[] fileContent = "test file content".getBytes();
        when(s3Service.downloadFile(testDocumentInfo.getFilePath())).thenReturn(fileContent);

        // Act
        byte[] result = documentService.getDocumentContent(TEST_DOCUMENT_ID, TEST_USERNAME, "download", true);

        // Assert
        assertNotNull(result);
        assertArrayEquals(fileContent, result);

        // Need to wait a bit for async operations
        verify(documentUserHistoryRepository, timeout(1000).times(1)).save(any());
        verify(documentPreferencesService, timeout(1000).times(1))
                .recordInteraction(eq(TEST_USER_ID), eq(TEST_DOCUMENT_ID), eq(InteractionType.DOWNLOAD));
    }

    @Test
    void getDocumentDetails_Success() {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));

        // Act
        DocumentInformation result = documentService.getDocumentDetails(TEST_DOCUMENT_ID, TEST_USERNAME, false);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_DOCUMENT_ID, result.getId());
        assertNull(result.getContent());
        verify(documentUserHistoryRepository, never()).save(any());
        verify(documentPreferencesService, never()).recordInteraction(any(), any(), any());
    }

    @Test
    void getDocumentDetails_WithHistory_RecordsView() {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));

        // Act
        DocumentInformation result = documentService.getDocumentDetails(TEST_DOCUMENT_ID, TEST_USERNAME, true);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_DOCUMENT_ID, result.getId());
        assertNull(result.getContent());

        // Verify async operations
        verify(documentUserHistoryRepository, timeout(1000).times(1)).save(any());
        verify(documentPreferencesService, timeout(1000).times(1))
                .recordInteraction(eq(TEST_USER_ID), eq(TEST_DOCUMENT_ID), eq(InteractionType.VIEW));
    }

    @Test
    void updateDocument_Success() {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(testDocumentInfo);

        Set<String> updatedMajors = new HashSet<>(List.of("CS", "Math"));
        Set<String> updatedCourseCodes = new HashSet<>(List.of("CS101", "MATH101"));
        Set<String> updatedCategories = new HashSet<>(List.of("Programming", "Mathematics"));
        Set<String> updatedTags = new HashSet<>(List.of("java", "programming", "algorithms"));

        DocumentUpdateRequest updateRequest = new DocumentUpdateRequest(
                "Updated summary",
                updatedCourseCodes,
                updatedMajors,
                "UG",
                updatedCategories,
                updatedTags
        );

        // Act
        DocumentInformation result = documentService.updateDocument(TEST_DOCUMENT_ID, updateRequest, TEST_USERNAME);

        // Assert
        assertNotNull(result);
        verify(documentRepository).save(any(DocumentInformation.class));

        // Capture the document being saved to verify updates
        ArgumentCaptor<DocumentInformation> documentCaptor = ArgumentCaptor.forClass(DocumentInformation.class);
        verify(documentRepository).save(documentCaptor.capture());
        DocumentInformation savedDoc = documentCaptor.getValue();

        assertEquals("Updated summary", savedDoc.getSummary());
        assertEquals(updatedMajors, savedDoc.getMajors());
        assertEquals(updatedCourseCodes, savedDoc.getCourseCodes());
        assertEquals(updatedCategories, savedDoc.getCategories());
        assertEquals(updatedTags, savedDoc.getTags());
    }

    @Test
    void updateDocumentWithFile_Success() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(2048L);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getOriginalFilename()).thenReturn("updated-file.pdf");
        when(s3Service.uploadFile(any(MultipartFile.class), eq("documents"))).thenReturn("documents/new-path/updated-file.pdf");
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(testDocumentInfo);

        DocumentUpdateRequest updateRequest = new DocumentUpdateRequest(
                "Updated summary",
                new HashSet<>(List.of("CS101", "MATH101")),
                new HashSet<>(List.of("CS", "Math")),
                "UG",
                new HashSet<>(List.of("Programming", "Mathematics")),
                new HashSet<>(List.of("java", "programming", "algorithms"))
        );

        // Act
        DocumentInformation result = documentService.updateDocumentWithFile(
                TEST_DOCUMENT_ID, multipartFile, updateRequest, TEST_USERNAME
        );

        // Assert
        assertNotNull(result);
        verify(s3Service).uploadFile(any(MultipartFile.class), eq("documents"));
        verify(documentRepository).save(any(DocumentInformation.class));

        // Verify document updated with new file info and version
        ArgumentCaptor<DocumentInformation> documentCaptor = ArgumentCaptor.forClass(DocumentInformation.class);
        verify(documentRepository).save(documentCaptor.capture());
        DocumentInformation savedDoc = documentCaptor.getValue();

        assertEquals(DocumentStatus.PENDING, savedDoc.getStatus());
        assertEquals("updated-file.pdf", savedDoc.getFilename());
        assertEquals(1, savedDoc.getCurrentVersion());
        assertEquals(2, savedDoc.getVersions().size());
    }

    @Test
    void deleteDocument_Success() {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));

        // Act
        documentService.deleteDocument(TEST_DOCUMENT_ID, TEST_USERNAME);

        // Assert
        ArgumentCaptor<DocumentInformation> documentCaptor = ArgumentCaptor.forClass(DocumentInformation.class);
        verify(documentRepository).save(documentCaptor.capture());
        DocumentInformation savedDoc = documentCaptor.getValue();

        assertTrue(savedDoc.isDeleted());
    }

    @Test
    void getPopularTags_WithPrefix_ReturnsMatchingTags() {
        // Arrange
        when(documentRepository.findDistinctTagsByPattern("prog")).thenReturn(List.of(
                createTagsResponse(Set.of("programming", "progress")),
                createTagsResponse(Set.of("programmer", "prognosis"))
        ));

        // Act
        Set<String> result = documentService.getPopularTags("prog");

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size());
        assertTrue(result.containsAll(List.of("programming", "progress", "programmer", "prognosis")));
    }

    @Test
    void getPopularTags_WithoutPrefix_ReturnsAllTags() {
        // Arrange
        when(documentRepository.findAllTags()).thenReturn(List.of(
                createTagsResponse(Set.of("java", "programming")),
                createTagsResponse(Set.of("python", "algorithm"))
        ));

        // Act
        Set<String> result = documentService.getPopularTags(null);

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size());
        assertTrue(result.containsAll(List.of("java", "programming", "python", "algorithm")));
    }

    @Test
    void getDocumentVersionContent_Success() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));
        byte[] versionContent = "version content".getBytes();
        when(s3Service.downloadFile(testDocumentVersion.getFilePath())).thenReturn(versionContent);

        // Act
        byte[] result = documentService.getDocumentVersionContent(
                TEST_DOCUMENT_ID, 0, TEST_USERNAME, "view", false
        );

        // Assert
        assertNotNull(result);
        assertArrayEquals(versionContent, result);
    }

    @Test
    void getDocumentVersionContent_VersionNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));

        // Act & Assert
        InvalidDocumentException exception = assertThrows(
                InvalidDocumentException.class,
                () -> documentService.getDocumentVersionContent(TEST_DOCUMENT_ID, 999, TEST_USERNAME, "view", false)
        );
        assertEquals("Version not found", exception.getMessage());
    }

    @Test
    void revertToVersion_Success() {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(testDocumentInfo);

        // Act
        DocumentInformation result = documentService.revertToVersion(TEST_DOCUMENT_ID, 0, TEST_USERNAME);

        // Assert
        assertNotNull(result);

        // Verify document updated correctly
        ArgumentCaptor<DocumentInformation> documentCaptor = ArgumentCaptor.forClass(DocumentInformation.class);
        verify(documentRepository).save(documentCaptor.capture());
        DocumentInformation savedDoc = documentCaptor.getValue();

        assertEquals(1, savedDoc.getCurrentVersion());
        assertEquals(2, savedDoc.getVersions().size());
        assertEquals(DocumentStatus.PENDING, savedDoc.getStatus());
    }

    @Test
    void revertToVersion_NotOwner_ThrowsException() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        UserResponse otherUserResponse = new UserResponse(
                otherUserId,
                "otheruser",
                "other@example.com",
                new com.dms.document.interaction.dto.RoleResponse(UUID.randomUUID(), AppRole.ROLE_USER)
        );

        // Mock responses for "otheruser"
        when(userClient.getUserByUsername("otheruser")).thenReturn(ResponseEntity.ok(otherUserResponse));

        // Mock document access for other user
        DocumentInformation accessibleDoc = testDocumentInfo.withContent(null);
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, otherUserId.toString()))
                .thenReturn(Optional.of(accessibleDoc));

        // Act & Assert
        InvalidDocumentException exception = assertThrows(
                InvalidDocumentException.class,
                () -> documentService.revertToVersion(TEST_DOCUMENT_ID, 0, "otheruser")
        );
        assertEquals("Only document creator can revert versions", exception.getMessage());
    }

    @Test
    void validateDocument_InvalidExtension_ThrowsException() {
        // Arrange
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getOriginalFilename()).thenReturn("test.docx"); // Wrong extension for PDF

        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));

        // Act & Assert
        InvalidDocumentException exception = assertThrows(
                InvalidDocumentException.class,
                () -> documentService.uploadDocument(
                        multipartFile, "Test document", null, null, "UG", null, null, TEST_USERNAME
                )
        );
        assertTrue(exception.getMessage().contains("File extension '.docx' does not match the expected type"));
    }

    @Test
    void userNotFound_ThrowsException() {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.notFound().build());

        // Act & Assert
        InvalidDataAccessResourceUsageException exception = assertThrows(
                InvalidDataAccessResourceUsageException.class,
                () -> documentService.getDocumentDetails(TEST_DOCUMENT_ID, TEST_USERNAME, false)
        );
        assertEquals("User not found", exception.getMessage());
    }

    // Helper method to create TagsResponse
    private com.dms.document.interaction.dto.TagsResponse createTagsResponse(Set<String> tags) {
        com.dms.document.interaction.dto.TagsResponse response = new com.dms.document.interaction.dto.TagsResponse();
        response.setTags(tags);
        return response;
    }

    @Test
    void uploadDocument_MentorRole_Success() throws IOException {
        // Arrange
        UserResponse mentorResponse = new UserResponse(
                TEST_USER_ID, TEST_USERNAME, "test@example.com",
                new com.dms.document.interaction.dto.RoleResponse(UUID.randomUUID(), AppRole.ROLE_MENTOR)
        );
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(mentorResponse));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getOriginalFilename()).thenReturn("testfile.pdf");
        when(s3Service.uploadFile(any(MultipartFile.class), eq("documents"))).thenReturn(TEST_S3_KEY);
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(testDocumentInfo);

        // Act
        DocumentInformation result = documentService.uploadDocument(
                multipartFile, "Test document", null, null, "UG", null, null, TEST_USERNAME
        );

        // Assert
        assertNotNull(result);
        assertEquals(TEST_DOCUMENT_ID, result.getId());
        verify(userClient).getUserByUsername(TEST_USERNAME);
        verify(s3Service).uploadFile(any(MultipartFile.class), eq("documents"));
        verify(documentRepository).save(any(DocumentInformation.class));
    }

    @Test
    void uploadDocument_InvalidRole_ThrowsException() throws IOException {
        // Arrange
        UserResponse adminResponse = new UserResponse(
                TEST_USER_ID, TEST_USERNAME, "test@example.com",
                new com.dms.document.interaction.dto.RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN)
        );
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(adminResponse));

        // Act & Assert
        InvalidDataAccessResourceUsageException exception = assertThrows(
                InvalidDataAccessResourceUsageException.class,
                () -> documentService.uploadDocument(
                        multipartFile, "Test document", null, null, "UG", null, null, TEST_USERNAME
                )
        );
        assertEquals("Invalid role", exception.getMessage());
    }

    @Test
    void getDocumentContent_AdminRole_Success() throws IOException {
        // Arrange
        UserResponse adminResponse = new UserResponse(
                TEST_USER_ID, TEST_USERNAME, "test@example.com",
                new com.dms.document.interaction.dto.RoleResponse(UUID.randomUUID(), AppRole.ROLE_ADMIN)
        );
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(adminResponse));
        when(documentRepository.findAccessibleDocumentById(TEST_DOCUMENT_ID)).thenReturn(Optional.of(testDocumentInfo));
        byte[] fileContent = "test file content".getBytes();
        when(s3Service.downloadFile(testDocumentInfo.getFilePath())).thenReturn(fileContent);

        // Act
        byte[] result = documentService.getDocumentContent(TEST_DOCUMENT_ID, TEST_USERNAME, "view", false);

        // Assert
        assertNotNull(result);
        assertArrayEquals(fileContent, result);
        verify(documentRepository).findAccessibleDocumentById(TEST_DOCUMENT_ID);
    }

    @Test
    void getDocumentThumbnail_S3DownloadFails_ReturnsErrorPlaceholder() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));
        when(s3Service.downloadFile(testDocumentInfo.getThumbnailPath())).thenThrow(new IOException("S3 error"));
        byte[] placeholderBytes = "error placeholder".getBytes();
        ByteArrayResource placeholderResource = new ByteArrayResource(placeholderBytes);
        when(errorPlaceholder.getInputStream()).thenReturn(placeholderResource.getInputStream());

        // Act
        ThumbnailResponse response = documentService.getDocumentThumbnail(TEST_DOCUMENT_ID, TEST_USERNAME);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(response.isPlaceholder());
        assertArrayEquals(placeholderBytes, response.getData());
    }

    @Test
    void validateDocument_NullFilename_DoesNotThrowExtensionMismatch() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getOriginalFilename()).thenReturn(null);
        when(s3Service.uploadFile(any(MultipartFile.class), eq("documents"))).thenReturn(TEST_S3_KEY);
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(testDocumentInfo);

        // Act
        DocumentInformation result = documentService.uploadDocument(
                multipartFile, "Test document", null, null, "UG", null, null, TEST_USERNAME
        );

        // Assert
        assertNotNull(result);
        verify(documentRepository).save(any(DocumentInformation.class));
    }

    @Test
    void validateDocument_ExcelInvalidExtension_ThrowsException() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.getContentType()).thenReturn("application/vnd.ms-excel");
        when(multipartFile.getOriginalFilename()).thenReturn("test.pdf"); // Wrong extension for Excel

        // Act & Assert
        InvalidDocumentException exception = assertThrows(
                InvalidDocumentException.class,
                () -> documentService.uploadDocument(
                        multipartFile, "Test document", null, null, "UG", null, null, TEST_USERNAME
                )
        );
        assertTrue(exception.getMessage().contains("File extension '.pdf' does not match the expected type"));
    }

    @Test
    void getFileExtension_NoExtension_ReturnsEmptyString() throws Exception {
        // Use reflection to access private method
        Method getFileExtension = DocumentServiceImpl.class.getDeclaredMethod("getFileExtension", String.class);
        getFileExtension.setAccessible(true);
        String result = (String) getFileExtension.invoke(documentService, "testfile");
        assertEquals("", result);
    }

    @Test
    void revertToVersion_NullThumbnailPath_Success() {
        // Arrange
        // Create a new DocumentVersion with null thumbnailPath using the builder
        DocumentVersion versionWithNullThumbnail = DocumentVersion.builder()
                .versionNumber(testDocumentVersion.getVersionNumber())
                .filePath(testDocumentVersion.getFilePath())
                .filename(testDocumentVersion.getFilename())
                .fileSize(testDocumentVersion.getFileSize())
                .mimeType(testDocumentVersion.getMimeType())
                .documentType(testDocumentVersion.getDocumentType())
                .status(testDocumentVersion.getStatus())
                .language(testDocumentVersion.getLanguage())
                .createdBy(testDocumentVersion.getCreatedBy())
                .createdAt(testDocumentVersion.getCreatedAt())
                .thumbnailPath(null) // Explicitly set thumbnailPath to null
                .build();

        // Create a new DocumentInformation with the modified version
        DocumentInformation docWithNullThumbnail = DocumentInformation.builder()
                .id(testDocumentInfo.getId())
                .status(testDocumentInfo.getStatus())
                .filename(testDocumentInfo.getFilename())
                .filePath(testDocumentInfo.getFilePath())
                .thumbnailPath(null) // Set thumbnailPath to null
                .fileSize(testDocumentInfo.getFileSize())
                .mimeType(testDocumentInfo.getMimeType())
                .documentType(testDocumentInfo.getDocumentType())
                .summary(testDocumentInfo.getSummary())
                .majors(testDocumentInfo.getMajors())
                .courseCodes(testDocumentInfo.getCourseCodes())
                .courseLevel(testDocumentInfo.getCourseLevel())
                .categories(testDocumentInfo.getCategories())
                .tags(testDocumentInfo.getTags())
                .userId(testDocumentInfo.getUserId())
                .sharingType(testDocumentInfo.getSharingType())
                .sharedWith(testDocumentInfo.getSharedWith())
                .deleted(testDocumentInfo.isDeleted())
                .currentVersion(testDocumentInfo.getCurrentVersion())
                .versions(List.of(versionWithNullThumbnail)) // Use the modified version
                .language(testDocumentInfo.getLanguage())
                .createdAt(testDocumentInfo.getCreatedAt())
                .createdBy(testDocumentInfo.getCreatedBy())
                .updatedAt(testDocumentInfo.getUpdatedAt())
                .updatedBy(testDocumentInfo.getUpdatedBy())
                .recommendationCount(testDocumentInfo.getRecommendationCount())
                .build();

        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(docWithNullThumbnail));
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(docWithNullThumbnail);

        // Act
        DocumentInformation result = documentService.revertToVersion(TEST_DOCUMENT_ID, 0, TEST_USERNAME);

        // Assert
        assertNotNull(result);
        ArgumentCaptor<DocumentInformation> documentCaptor = ArgumentCaptor.forClass(DocumentInformation.class);
        verify(documentRepository).save(documentCaptor.capture());
        DocumentInformation savedDoc = documentCaptor.getValue();
        assertNull(savedDoc.getThumbnailPath());
        assertEquals(1, savedDoc.getCurrentVersion());
    }

    @Test
    void uploadDocument_NullInputSets_Success() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getOriginalFilename()).thenReturn("testfile.pdf");
        when(s3Service.uploadFile(any(MultipartFile.class), eq("documents"))).thenReturn(TEST_S3_KEY);
        when(documentRepository.save(any(DocumentInformation.class))).thenReturn(testDocumentInfo);

        // Act
        DocumentInformation result = documentService.uploadDocument(
                multipartFile, "Test document", null, null, "UG", null, null, TEST_USERNAME
        );

        // Assert
        assertNotNull(result);
        ArgumentCaptor<DocumentInformation> captor = ArgumentCaptor.forClass(DocumentInformation.class);
        verify(documentRepository).save(captor.capture());
        DocumentInformation savedDoc = captor.getValue();
        assertTrue(savedDoc.getMajors().isEmpty());
        assertTrue(savedDoc.getCourseCodes().isEmpty());
        assertTrue(savedDoc.getCategories().isEmpty());
        assertTrue(savedDoc.getTags().isEmpty());
    }

    @Test
    void getDocumentThumbnail_ProcessingDocument_ReturnsProcessingPlaceholder() throws IOException {
        // Arrange
        DocumentInformation processingDoc = DocumentInformation.builder()
                .id(testDocumentInfo.getId())
                .status(DocumentStatus.PROCESSING) // Modified status
                .filename(testDocumentInfo.getFilename())
                .filePath(testDocumentInfo.getFilePath())
                .thumbnailPath(testDocumentInfo.getThumbnailPath())
                .fileSize(testDocumentInfo.getFileSize())
                .mimeType(testDocumentInfo.getMimeType())
                .documentType(testDocumentInfo.getDocumentType())
                .summary(testDocumentInfo.getSummary())
                .majors(testDocumentInfo.getMajors())
                .courseCodes(testDocumentInfo.getCourseCodes())
                .courseLevel(testDocumentInfo.getCourseLevel())
                .categories(testDocumentInfo.getCategories())
                .tags(testDocumentInfo.getTags())
                .userId(testDocumentInfo.getUserId())
                .sharingType(testDocumentInfo.getSharingType())
                .sharedWith(testDocumentInfo.getSharedWith())
                .deleted(testDocumentInfo.isDeleted())
                .currentVersion(testDocumentInfo.getCurrentVersion())
                .versions(testDocumentInfo.getVersions())
                .language(testDocumentInfo.getLanguage())
                .createdAt(testDocumentInfo.getCreatedAt())
                .createdBy(testDocumentInfo.getCreatedBy())
                .updatedAt(testDocumentInfo.getUpdatedAt())
                .updatedBy(testDocumentInfo.getUpdatedBy())
                .recommendationCount(testDocumentInfo.getRecommendationCount())
                .build();

        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(processingDoc));
        byte[] placeholderBytes = "processing placeholder".getBytes();
        ByteArrayResource placeholderResource = new ByteArrayResource(placeholderBytes);
        when(processingPlaceholder.getInputStream()).thenReturn(placeholderResource.getInputStream());

        // Act
        ThumbnailResponse response = documentService.getDocumentThumbnail(TEST_DOCUMENT_ID, TEST_USERNAME);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        assertTrue(response.isPlaceholder());
        assertArrayEquals(placeholderBytes, response.getData());
    }


    @Test
    void getDocumentContent_NullFileContent_ReturnsNull() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(testDocumentInfo));
        when(s3Service.downloadFile(testDocumentInfo.getFilePath())).thenReturn(null);

        // Act
        byte[] result = documentService.getDocumentContent(TEST_DOCUMENT_ID, TEST_USERNAME, "download", true);

        // Assert
        assertNull(result);
        verify(documentUserHistoryRepository, never()).save(any()); // No history recorded
    }

    @Test
    void validateDocument_WordInvalidExtension_ThrowsException() throws IOException {
        // Arrange
        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.getContentType()).thenReturn("application/msword");
        when(multipartFile.getOriginalFilename()).thenReturn("test.pdf"); // Wrong extension for Word

        // Act & Assert
        InvalidDocumentException exception = assertThrows(
                InvalidDocumentException.class,
                () -> documentService.uploadDocument(
                        multipartFile, "Test document", null, null, "UG", null, null, TEST_USERNAME
                )
        );
        assertTrue(exception.getMessage().contains("File extension '.pdf' does not match the expected type"));
    }

    @Test
    void getDocumentThumbnail_EmptyThumbnailPath_ReturnsErrorPlaceholder() throws IOException {
        // Arrange
        DocumentInformation docWithEmptyThumbnail = DocumentInformation.builder()
                .id(testDocumentInfo.getId())
                .status(testDocumentInfo.getStatus())
                .filename(testDocumentInfo.getFilename())
                .filePath(testDocumentInfo.getFilePath())
                .thumbnailPath("") // Modified to empty string
                .fileSize(testDocumentInfo.getFileSize())
                .mimeType(testDocumentInfo.getMimeType())
                .documentType(testDocumentInfo.getDocumentType())
                .summary(testDocumentInfo.getSummary())
                .majors(testDocumentInfo.getMajors())
                .courseCodes(testDocumentInfo.getCourseCodes())
                .courseLevel(testDocumentInfo.getCourseLevel())
                .categories(testDocumentInfo.getCategories())
                .tags(testDocumentInfo.getTags())
                .userId(testDocumentInfo.getUserId())
                .sharingType(testDocumentInfo.getSharingType())
                .sharedWith(testDocumentInfo.getSharedWith())
                .deleted(testDocumentInfo.isDeleted())
                .currentVersion(testDocumentInfo.getCurrentVersion())
                .versions(testDocumentInfo.getVersions())
                .language(testDocumentInfo.getLanguage())
                .createdAt(testDocumentInfo.getCreatedAt())
                .createdBy(testDocumentInfo.getCreatedBy())
                .updatedAt(testDocumentInfo.getUpdatedAt())
                .updatedBy(testDocumentInfo.getUpdatedBy())
                .recommendationCount(testDocumentInfo.getRecommendationCount())
                .build();

        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(docWithEmptyThumbnail));
        byte[] placeholderBytes = "error placeholder".getBytes();
        ByteArrayResource placeholderResource = new ByteArrayResource(placeholderBytes);
        when(errorPlaceholder.getInputStream()).thenReturn(placeholderResource.getInputStream());

        // Act
        ThumbnailResponse response = documentService.getDocumentThumbnail(TEST_DOCUMENT_ID, TEST_USERNAME);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertTrue(response.isPlaceholder());
        assertArrayEquals(placeholderBytes, response.getData());
    }

    @Test
    void revertToVersion_NullCreatedBy_ThrowsException() {
        // Arrange
        DocumentInformation docWithNullCreatedBy = DocumentInformation.builder()
                .id(testDocumentInfo.getId())
                .status(testDocumentInfo.getStatus())
                .filename(testDocumentInfo.getFilename())
                .filePath(testDocumentInfo.getFilePath())
                .thumbnailPath(testDocumentInfo.getThumbnailPath())
                .fileSize(testDocumentInfo.getFileSize())
                .mimeType(testDocumentInfo.getMimeType())
                .documentType(testDocumentInfo.getDocumentType())
                .summary(testDocumentInfo.getSummary())
                .majors(testDocumentInfo.getMajors())
                .courseCodes(testDocumentInfo.getCourseCodes())
                .courseLevel(testDocumentInfo.getCourseLevel())
                .categories(testDocumentInfo.getCategories())
                .tags(testDocumentInfo.getTags())
                .userId(testDocumentInfo.getUserId())
                .sharingType(testDocumentInfo.getSharingType())
                .sharedWith(testDocumentInfo.getSharedWith())
                .deleted(testDocumentInfo.isDeleted())
                .currentVersion(testDocumentInfo.getCurrentVersion())
                .versions(testDocumentInfo.getVersions())
                .language(testDocumentInfo.getLanguage())
                .createdAt(testDocumentInfo.getCreatedAt())
                .createdBy(null) // Modified to null
                .updatedAt(testDocumentInfo.getUpdatedAt())
                .updatedBy(testDocumentInfo.getUpdatedBy())
                .recommendationCount(testDocumentInfo.getRecommendationCount())
                .build();

        when(userClient.getUserByUsername(TEST_USERNAME)).thenReturn(ResponseEntity.ok(testUserResponse));
        when(documentRepository.findAccessibleDocumentByIdAndUserId(TEST_DOCUMENT_ID, TEST_USER_ID.toString()))
                .thenReturn(Optional.of(docWithNullCreatedBy));

        // Act & Assert
        InvalidDocumentException exception = assertThrows(
                InvalidDocumentException.class,
                () -> documentService.revertToVersion(TEST_DOCUMENT_ID, 0, TEST_USERNAME)
        );
        assertEquals("Only document creator can revert versions", exception.getMessage());
    }

    @Test
    void getFileExtensionAndName_VariousFilenames() throws Exception {
        // Use reflection to access private methods
        Method getFileExtension = DocumentServiceImpl.class.getDeclaredMethod("getFileExtension", String.class);
        Method getFileName = DocumentServiceImpl.class.getDeclaredMethod("getFileName", String.class);
        getFileExtension.setAccessible(true);
        getFileName.setAccessible(true);

        // Test with extension
        assertEquals(".pdf", getFileExtension.invoke(documentService, "test.pdf"));
        assertEquals("test", getFileName.invoke(documentService, "test.pdf"));

        // Test without extension
        assertEquals("", getFileExtension.invoke(documentService, "test"));
        assertEquals("", getFileName.invoke(documentService, "test")); // Fixed expectation

        // Test with null
        assertEquals("", getFileExtension.invoke(documentService, (Object) null));
        assertEquals("", getFileName.invoke(documentService, (Object) null));
    }

    @Test
    void createStoragePath_GeneratesCorrectPath() throws Exception {
        // Use reflection to access private method
        Method createStoragePath = DocumentServiceImpl.class.getDeclaredMethod("createStoragePath", String.class);
        createStoragePath.setAccessible(true);

        // Mock current date as 2025-04-10 (assuming test runs on this date)
        String result = (String) createStoragePath.invoke(documentService, "testfile.pdf");
        assertThat(result, containsString("testfile.pdf"));
    }
}