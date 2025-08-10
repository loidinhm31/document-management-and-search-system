package com.dms.processor.service.impl;

import com.dms.processor.config.FileStorageProperties;
import com.dms.processor.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.file-storage.type", havingValue = "S3", matchIfMissing = true)
public class S3FileStorageServiceImpl implements FileStorageService {

    private final S3Client s3Client;
    private final FileStorageProperties fileStorageProperties;

    @Override
    public String uploadFile(Path filePath, String prefix, String contentType) throws IOException {
        String key = generateS3Key(filePath.getFileName().toString(), prefix);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(fileStorageProperties.getS3().getBucketName())
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromFile(filePath));
        log.info("File uploaded successfully to S3: {}", key);
        return key;
    }

    @Override
    public Path downloadToTemp(String s3Key) throws IOException {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(fileStorageProperties.getS3().getBucketName())
                    .key(s3Key)
                    .build();

            // Create temp directory
            Path tempDir = Files.createTempDirectory("s3-download-");

            // Extract filename from S3 key
            String filename = Paths.get(s3Key).getFileName().toString();
            Path tempFile = tempDir.resolve(filename);

            // Download and save to temp file
            try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest)) {
                Files.copy(s3Object, tempFile);
            }

            log.info("File downloaded from S3 to temp: {}", tempFile);
            return tempFile;
        } catch (S3Exception e) {
            log.error("Error downloading file from S3: {}", e.getMessage());
            throw new IOException("Failed to download file from S3: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(fileStorageProperties.getS3().getBucketName())
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("File deleted successfully from S3: {}", key);
        } catch (S3Exception e) {
            log.error("Error deleting file from S3: {}", e.getMessage());
        }
    }

    @Override
    public void cleanup(Path tempPath) {
        try {
            if (Files.exists(tempPath)) {
                if (Files.isDirectory(tempPath)) {
                    FileUtils.deleteDirectory(tempPath.toFile());
                } else {
                    Files.deleteIfExists(tempPath);
                }
                log.debug("Cleaned up temp path: {}", tempPath);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp path: {}", tempPath, e);
        }
    }

    private String generateS3Key(String originalFilename, String prefix) {
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());

        // Clean filename
        String cleanFilename = Normalizer.normalize(originalFilename, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");

        String uniqueId = UUID.randomUUID().toString();

        return String.format("%s/%s/%s-%s", prefix, datePath, uniqueId, cleanFilename);
    }
}