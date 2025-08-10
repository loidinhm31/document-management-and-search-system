package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.config.FileStorageProperties;
import com.dms.document.interaction.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.file-storage.type", havingValue = "S3", matchIfMissing = true)
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final FileStorageProperties fileStorageProperties;

    @Override
    public String uploadFile(MultipartFile file, String prefix) throws IOException {
        String key = generateS3Key(file.getOriginalFilename(), prefix);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(fileStorageProperties.getS3().getBucketName())
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        log.info("File uploaded successfully to S3: {}", key);
        return key;
    }

    @Override
    public byte[] downloadFile(String key) throws IOException {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(fileStorageProperties.getS3().getBucketName())
                    .key(key)
                    .build();

            return s3Client.getObject(getObjectRequest, ResponseTransformer.toBytes()).asByteArray();
        } catch (S3Exception e) {
            log.error("Error downloading file from S3: {}", e.getMessage());
            throw new IOException("Failed to download file from S3", e);
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

    private String generateS3Key(String originalFilename, String prefix) {
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String uniqueId = String.valueOf(Instant.now().toEpochMilli());

        return String.format("%s/%s/%s-%s", prefix, datePath, uniqueId, originalFilename);
    }
}