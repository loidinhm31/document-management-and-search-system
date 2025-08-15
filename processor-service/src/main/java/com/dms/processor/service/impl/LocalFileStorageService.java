package com.dms.processor.service.impl;

import com.dms.processor.config.FileStorageProperties;
import com.dms.processor.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.file-storage.type", havingValue = "LOCAL")
public class LocalFileStorageService implements FileStorageService {

    private final FileStorageProperties fileStorageProperties;

    @Override
    public String uploadFile(Path filePath, String prefix, String contentType) throws IOException {
        String fileName = filePath.getFileName().toString();
        String cleanFilename = Normalizer.normalize(fileName, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        String storagePath = generateFilePath(cleanFilename, prefix);

        Path targetLocation = getUploadPath().resolve(storagePath);

        // Create directories if they don't exist
        Files.createDirectories(targetLocation.getParent());

        // Copy file to target location
        Files.copy(filePath, targetLocation, StandardCopyOption.REPLACE_EXISTING);

        log.info("File uploaded successfully to local storage: {}", storagePath);
        return storagePath;
    }

    @Override
    public Path downloadToTemp(String filePath) throws IOException {
        try {
            Path sourceLocation = getUploadPath().resolve(filePath);

            if (!Files.exists(sourceLocation)) {
                throw new IOException("File not found: " + filePath);
            }

            // Create temp directory if it doesn't exist
            Path tempDir = getTempPath();
            Files.createDirectories(tempDir);

            // Create temp file with original filename
            String originalFilename = Paths.get(filePath).getFileName().toString();
            Path tempFile = tempDir.resolve(UUID.randomUUID() + "_" + originalFilename);

            // Copy to temp location
            Files.copy(sourceLocation, tempFile, StandardCopyOption.REPLACE_EXISTING);

            log.info("File downloaded to temp location: {}", tempFile);
            return tempFile;
        } catch (IOException e) {
            log.error("Error downloading file from local storage: {}", e.getMessage());
            throw new IOException("Failed to download file from local storage", e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            Path targetLocation = getUploadPath().resolve(filePath);
            Files.deleteIfExists(targetLocation);
            log.info("File deleted successfully from local storage: {}", filePath);
        } catch (IOException e) {
            log.error("Error deleting file from local storage: {}", e.getMessage());
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

    private Path getUploadPath() {
        return Paths.get(fileStorageProperties.getLocal().getUploadDir()).toAbsolutePath().normalize();
    }

    private Path getTempPath() {
        return Paths.get(fileStorageProperties.getLocal().getTempDir()).toAbsolutePath().normalize();
    }

    private String generateFilePath(String originalFilename, String prefix) {
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String uniqueId = String.valueOf(Instant.now().toEpochMilli());

        return String.format("%s/%s/%s-%s", prefix, datePath, uniqueId, originalFilename);
    }
}