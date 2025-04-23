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
import java.text.Normalizer;
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

            String originalFileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);

            // Sanitize the filename to handle international characters properly
            String sanitizedFileName = sanitizeFileName(originalFileName);

            // Create a unique directory for each download to avoid collisions
            Path tempDir = Path.of(s3Properties.getTempDir(), UUID.randomUUID().toString());
            Files.createDirectories(tempDir);

            // Use the sanitized filename for the temporary file
            Path tempFile = tempDir.resolve(sanitizedFileName);

            log.info("Downloading S3 object '{}' to temp file '{}'", s3Key, tempFile);

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest);
            FileUtils.copyInputStreamToFile(response, tempFile.toFile());

            return tempFile;
        } catch (S3Exception e) {
            log.error("Error downloading file from S3: {}", e.getMessage());
            throw new IOException("Failed to download file from S3", e);
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unnamed_" + UUID.randomUUID();
        }

        // Normalize and remove diacritics
        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");

        // Replace any remaining non-ASCII or problematic characters with underscores
        String sanitized = normalized.replaceAll("[^a-zA-Z0-9._\\-]", "_");

        // Add a timestamp prefix if the file was completely sanitized to ensure uniqueness
        if (sanitized.equals("_") || sanitized.isEmpty()) {
            sanitized = "file_" + System.currentTimeMillis();
        }

        // Preserve the original extension if possible
        if (fileName.contains(".") && !sanitized.contains(".")) {
            String extension = fileName.substring(fileName.lastIndexOf('.'));
            if (extension.matches("\\.[a-zA-Z0-9]+")) {
                sanitized += extension;
            }
        }

        // Log the transformation for debugging
        if (!fileName.equals(sanitized)) {
            log.info("Sanitized filename: '{}' -> '{}'", fileName, sanitized);
        }

        return sanitized;
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