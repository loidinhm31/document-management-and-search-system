package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.config.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ServiceImplTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Properties s3Properties;

    @InjectMocks
    private S3ServiceImpl s3Service;

    private final String bucketName = "test-bucket";
    private final String testFileContent = "This is a test file content";
    private final String testFileName = "test-file.txt";
    private final String testContentType = "text/plain";
    private final String testPrefix = "documents";

    @BeforeEach
    void setUp() {
        when(s3Properties.getBucketName()).thenReturn(bucketName);
    }

    @Test
    void uploadFile_ShouldReturnS3Key_WhenUploadSuccessful() throws IOException {
        // Arrange
        MultipartFile mockFile = new MockMultipartFile(
                "file",
                testFileName,
                testContentType,
                testFileContent.getBytes()
        );

        // Act
        String s3Key = s3Service.uploadFile(mockFile, testPrefix);

        // Assert
        verify(s3Client).putObject((PutObjectRequest) any(), any(RequestBody.class));
        assertTrue(s3Key.startsWith(testPrefix));
        assertTrue(s3Key.contains(testFileName));

        // Verify the date format in the key
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        assertTrue(s3Key.contains(datePath));
    }

    @Test
    void downloadFile_ShouldReturnFileContent_WhenDownloadSuccessful() throws IOException {
        // Arrange
        String testKey = "test-key";
        byte[] expectedContent = testFileContent.getBytes();

        // Create mock GetObjectResponse and ResponseInputStream
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        ResponseInputStream<GetObjectResponse> responseInputStream =
                new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream(expectedContent));

        when(s3Client.getObject((GetObjectRequest) any())).thenReturn(responseInputStream);

        // Act
        byte[] actualContent = s3Service.downloadFile(testKey);

        // Assert
        verify(s3Client).getObject((GetObjectRequest) any());
        assertArrayEquals(expectedContent, actualContent);
    }

    @Test
    void downloadFile_ShouldThrowIOException_WhenS3ExceptionOccurs() {
        // Arrange
        String testKey = "test-key";
        when(s3Client.getObject((GetObjectRequest) any()))
                .thenThrow(S3Exception.builder().message("S3 Error").build());

        // Act & Assert
        assertThrows(IOException.class, () -> s3Service.downloadFile(testKey));
        verify(s3Client).getObject((GetObjectRequest) any());
    }

    @Test
    void deleteFile_ShouldCallS3Client_WhenDeleteSuccessful() {
        // Arrange
        String testKey = "test-key";

        // Act
        s3Service.deleteFile(testKey);

        // Assert
        verify(s3Client).deleteObject((DeleteObjectRequest) any());
    }

    @Test
    void deleteFile_ShouldNotThrowException_WhenS3ExceptionOccurs() {
        // Arrange
        String testKey = "test-key";
        doThrow(S3Exception.builder().message("S3 Error").build())
                .when(s3Client).deleteObject((DeleteObjectRequest) any());

        // Act & Assert
        assertDoesNotThrow(() -> s3Service.deleteFile(testKey));
        verify(s3Client).deleteObject((DeleteObjectRequest) any());
    }

    @Test
    void uploadFile_ShouldUseCorrectParameters_WhenInvoked() throws IOException {
        // Arrange
        MultipartFile mockFile = new MockMultipartFile(
                "file",
                testFileName,
                testContentType,
                testFileContent.getBytes()
        );

        // Act
        s3Service.uploadFile(mockFile, testPrefix);

        // Assert - Verify correct parameters are used
        verify(s3Properties).getBucketName();
        verify(s3Client).putObject(
                (PutObjectRequest) argThat(request ->
                        ((PutObjectRequest) request).bucket().equals(bucketName) &&
                        ((PutObjectRequest) request).contentType().equals(testContentType) &&
                        ((PutObjectRequest) request).key().startsWith(testPrefix)
                ),
                any(RequestBody.class)
        );
    }

    @Test
    void downloadFile_ShouldUseCorrectParameters_WhenInvoked() throws IOException {
        // Arrange
        String testKey = "test-key";
        byte[] expectedContent = testFileContent.getBytes();

        // Create mock GetObjectResponse and ResponseInputStream
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        ResponseInputStream<GetObjectResponse> responseInputStream =
                new ResponseInputStream<>(getObjectResponse, new ByteArrayInputStream(expectedContent));

        when(s3Client.getObject((GetObjectRequest) any())).thenReturn(responseInputStream);

        // Act
        s3Service.downloadFile(testKey);

        // Assert - Verify correct parameters are used
        verify(s3Properties).getBucketName();
        verify(s3Client).getObject(
                (GetObjectRequest) argThat(request ->
                        ((GetObjectRequest) request).bucket().equals(bucketName) &&
                        ((GetObjectRequest) request).key().equals(testKey)
                )
        );
    }

    @Test
    void deleteFile_ShouldUseCorrectParameters_WhenInvoked() {
        // Arrange
        String testKey = "test-key";

        // Act
        s3Service.deleteFile(testKey);

        // Assert - Verify correct parameters are used
        verify(s3Properties).getBucketName();
        verify(s3Client).deleteObject(
                (DeleteObjectRequest) argThat(request ->
                        ((DeleteObjectRequest) request).bucket().equals(bucketName) &&
                        ((DeleteObjectRequest) request).key().equals(testKey)
                )
        );
    }
}