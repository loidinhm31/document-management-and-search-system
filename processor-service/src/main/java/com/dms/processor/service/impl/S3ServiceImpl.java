package com.dms.processor.service.impl;

import com.dms.processor.config.S3Properties;
import com.dms.processor.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public String uploadFile(Path filePath, String prefix, String contentType) throws IOException {
        String key = generateS3Key(filePath.getFileName().toString(), prefix);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucketName())
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromFile(filePath));
        return key;
    }

    public Path downloadToTemp(String s3Key) throws IOException {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(s3Key)
                    .build();

            String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);
            Path tempDir = Path.of(s3Properties.getTempDir(), UUID.randomUUID().toString());
            Files.createDirectories(tempDir);
            Path tempFile = tempDir.resolve(fileName);

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest);
            FileUtils.copyInputStreamToFile(response, tempFile.toFile());

            return tempFile;
        } catch (S3Exception e) {
            log.error("Error downloading file from S3: {}", e.getMessage());
            throw new IOException("Failed to download file from S3", e);
        }
    }

    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        } catch (S3Exception e) {
            log.error("Error deleting file from S3: {}", e.getMessage());
        }
    }

    public void cleanup(Path tempPath) {
        try {
            FileUtils.deleteDirectory(tempPath.getParent().toFile());
        } catch (IOException e) {
            log.error("Error cleaning up temp directory: {}", e.getMessage());
        }
    }

    private String generateS3Key(String filename, String prefix) {
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String uniqueId = String.valueOf(Instant.now().toEpochMilli());

        return String.format("%s/%s/%s-%s", prefix, datePath, uniqueId, filename);
    }
}