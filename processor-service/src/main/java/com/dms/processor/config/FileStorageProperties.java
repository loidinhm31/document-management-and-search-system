package com.dms.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.file-storage")
public class FileStorageProperties {
    private StorageType type = StorageType.S3; // Default to S3
    private LocalStorage local = new LocalStorage();
    private S3Storage s3 = new S3Storage();

    public enum StorageType {
        LOCAL, S3
    }

    @Data
    public static class LocalStorage {
        private String uploadDir = "uploads"; // Default upload directory
        private String tempDir = "temp"; // Default temp directory
    }

    @Data
    public static class S3Storage {
        private String bucketName;
        private String region;
        private String accessKey;
        private String secretKey;
    }
}