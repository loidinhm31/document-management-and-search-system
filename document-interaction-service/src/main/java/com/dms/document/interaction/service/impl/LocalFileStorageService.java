package com.dms.document.interaction.service.impl;

import com.dms.document.interaction.config.FileStorageProperties;
import com.dms.document.interaction.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.file-storage.type", havingValue = "LOCAL")
public class LocalFileStorageService implements FileStorageService {

    private final FileStorageProperties fileStorageProperties;

    @Override
    public String uploadFile(MultipartFile file, String prefix) throws IOException {
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        String filePath = generateFilePath(fileName, prefix);

        Path targetLocation = getUploadPath().resolve(filePath);

        // Create directories if they don't exist
        Files.createDirectories(targetLocation.getParent());

        // Copy file to target location
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        log.info("File uploaded successfully to local storage: {}", filePath);
        return filePath;
    }

    @Override
    public byte[] downloadFile(String filePath) throws IOException {
        try {
            Path targetLocation = getUploadPath().resolve(filePath);

            if (!Files.exists(targetLocation)) {
                throw new IOException("File not found: " + filePath);
            }

            return Files.readAllBytes(targetLocation);
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

    private Path getUploadPath() {
        return Paths.get(fileStorageProperties.getLocal().getUploadDir()).toAbsolutePath().normalize();
    }

    private String generateFilePath(String originalFilename, String prefix) {
        LocalDate now = LocalDate.now();
        String datePath = String.format("%d/%02d/%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String uniqueId = String.valueOf(Instant.now().toEpochMilli());

        return String.format("%s/%s/%s-%s", prefix, datePath, uniqueId, originalFilename);
    }
}