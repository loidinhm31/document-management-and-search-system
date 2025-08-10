package com.dms.processor.service.impl;

import com.dms.processor.config.FileStorageProperties;
import com.dms.processor.service.FileStorageService;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ServiceImplTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private FileStorageProperties fileStorageProperties;

    @InjectMocks
    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    private Path testFile;
    private String bucketName = "test-bucket";
    private String testFileContent = "This is test content for S3 upload.";

    @BeforeEach
    void setUp() throws IOException {
        // Create a test file
        testFile = tempDir.resolve("test-file.txt");
        Files.write(testFile, testFileContent.getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up any files created during tests
        Files.deleteIfExists(testFile);
    }

    @Test
    void uploadFile_shouldUploadFileToS3AndReturnKey() throws IOException {
        // Arrange
        String contentType = "text/plain";
        String prefix = "documents";

        // Setup mocks for this specific test
        when(fileStorageProperties.getS3().getBucketName()).thenReturn(bucketName);

        // Mock the PutObjectResponse
        PutObjectResponse putObjectResponse = PutObjectResponse.builder().build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(putObjectResponse);

        // Act
        String resultKey = fileStorageService.uploadFile(testFile, prefix, contentType);

        // Assert
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertNotNull(resultKey);
        assertTrue(resultKey.startsWith(prefix + "/"));
        assertTrue(resultKey.endsWith(testFile.getFileName().toString()));
    }

    @Test
    void downloadToTemp_shouldDownloadFileFromS3() throws IOException {
        // Arrange
        String s3Key = "documents/2023/04/01/123456-test-file.txt";

        // Setup mocks for this specific test
        when(fileStorageProperties.getS3().getBucketName()).thenReturn(bucketName);
        when(fileStorageProperties.getLocal().getTempDir()).thenReturn(tempDir.toString());

        // Create mock response
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testFileContent.getBytes(StandardCharsets.UTF_8));
        ResponseInputStream<GetObjectResponse> responseInputStream =
                new ResponseInputStream<>(getObjectResponse, AbortableInputStream.create(inputStream));

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

        // Act
        Path downloadedFile = fileStorageService.downloadToTemp(s3Key);

        // Assert
        verify(s3Client).getObject(any(GetObjectRequest.class));
        assertNotNull(downloadedFile);
        assertTrue(Files.exists(downloadedFile));
        assertEquals(testFileContent, Files.readString(downloadedFile));
        // Fix: The filename in the S3 key includes the prefix "123456-"
        assertEquals("123456-test-file.txt", downloadedFile.getFileName().toString());

        // Clean up downloaded file
        Files.deleteIfExists(downloadedFile);
        if (downloadedFile.getParent() != null && !downloadedFile.getParent().equals(tempDir)) {
            FileUtils.deleteDirectory(downloadedFile.getParent().toFile());
        }
    }

    @Test
    void downloadToTemp_shouldThrowIOException_whenS3ClientFails() {
        // Arrange
        String s3Key = "documents/non-existent-file.txt";

        // Setup mocks for this specific test
        when(fileStorageProperties.getS3().getBucketName()).thenReturn(bucketName);
        when(fileStorageProperties.getLocal().getTempDir()).thenReturn(tempDir.toString());

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("File not found").build());

        // Act & Assert
        assertThrows(IOException.class, () -> fileStorageService.downloadToTemp(s3Key));
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void deleteFile_shouldDeleteFileFromS3() {
        // Arrange
        String s3Key = "documents/test-file.txt";

        // Setup mock for this specific test
        when(fileStorageProperties.getS3().getBucketName()).thenReturn(bucketName);

        DeleteObjectResponse deleteObjectResponse = DeleteObjectResponse.builder().build();
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(deleteObjectResponse);

        // Act
        fileStorageService.deleteFile(s3Key);

        // Assert
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteFile_shouldHandleS3Exception() {
        // Arrange
        String s3Key = "documents/test-file.txt";

        // Setup mock for this specific test
        when(fileStorageProperties.getS3().getBucketName()).thenReturn(bucketName);

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("Error deleting file").build());

        // Act - should not throw exception
        fileStorageService.deleteFile(s3Key);

        // Assert
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void cleanup_shouldDeleteDirectory() throws IOException {
        // Arrange
        Path tempPath = Files.createTempDirectory(tempDir, "temp-dir");
        Path testTempFile = tempPath.resolve("temp-file.txt");
        Files.write(testTempFile, "test content".getBytes(StandardCharsets.UTF_8));

        // Act
        fileStorageService.cleanup(testTempFile);

        // Assert
        assertFalse(Files.exists(tempPath));
    }

    @Test
    void cleanup_shouldHandleIOException() throws IOException {
        // Create a real temporary file for testing
        Path tempPath = Files.createTempDirectory(tempDir, "cleanup-test");
        Path tempFile = tempPath.resolve("test.txt");
        Files.createFile(tempFile);

        // Create a spy on the real path
        Path tempFileSpy = spy(tempFile);

        // Make getParent() throw an IOException when called
        // This simulates a filesystem error during cleanup
        doAnswer(invocation -> {
            throw new IOException("Test IO exception");
        }).when(tempFileSpy).getParent();

        // Act - this should not throw exception despite the IOException
        fileStorageService.cleanup(tempFileSpy);

        // Verify the test doesn't throw an exception

        // Clean up our real temp directory
        Files.deleteIfExists(tempFile);
        Files.deleteIfExists(tempPath);
    }

    @Test
    void generateS3Key_shouldCreateKeyWithCorrectFormat() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method generateS3KeyMethod;

        // Fix: Removed unnecessary stub for getBucketName()

        generateS3KeyMethod = S3FileStorageServiceImpl.class.getDeclaredMethod("generateS3Key", String.class, String.class);
        generateS3KeyMethod.setAccessible(true);

        // Arrange
        String filename = "test-file.txt";
        String prefix = "documents";

        // Act
        String key = (String) generateS3KeyMethod.invoke(fileStorageService, filename, prefix);

        // Assert
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());

        assertTrue(key.startsWith(prefix + "/" + datePath + "/"));
        assertTrue(key.endsWith(filename));
    }
}