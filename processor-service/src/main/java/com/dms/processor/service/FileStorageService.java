package com.dms.processor.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for file storage operations.
 * Provides methods for uploading, downloading, and managing files in different storage systems.
 */
public interface FileStorageService {

    /**
     * Uploads a file to the configured storage
     *
     * @param filePath the path to the file to upload
     * @param prefix the prefix to prepend to the file path/key
     * @param contentType the content type of the file
     * @return the file path or key of the uploaded file
     * @throws IOException if the upload fails
     */
    String uploadFile(Path filePath, String prefix, String contentType) throws IOException;

    /**
     * Downloads a file from the configured storage to a temporary location
     *
     * @param filePathOrKey the file path or key of the file to download
     * @return Path to the downloaded temporary file
     * @throws IOException if the download fails
     */
    Path downloadToTemp(String filePathOrKey) throws IOException;

    /**
     * Deletes a file from the configured storage
     *
     * @param filePathOrKey the file path or key of the file to delete
     */
    void deleteFile(String filePathOrKey);

    /**
     * Cleans up temporary files and directories
     *
     * @param tempPath Path to the temporary file to clean up
     */
    void cleanup(Path tempPath);
}