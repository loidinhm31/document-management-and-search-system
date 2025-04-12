package com.dms.processor.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for AWS S3 operations.
 * Provides methods for uploading, downloading, and managing files in S3.
 */
public interface S3Service {

    /**
     * Uploads a file to S3 bucket.
     *
     * @param filePath    Path to the file to be uploaded
     * @param prefix      Prefix for the S3 object key
     * @param contentType Content type of the file
     * @return The generated S3 object key
     * @throws IOException If an I/O error occurs during the upload
     */
    String uploadFile(Path filePath, String prefix, String contentType) throws IOException;

    /**
     * Downloads a file from S3 to a temporary location.
     *
     * @param s3Key The S3 object key of the file to download
     * @return Path to the downloaded temporary file
     * @throws IOException If an I/O error occurs during the download
     */
    Path downloadToTemp(String s3Key) throws IOException;

    /**
     * Deletes a file from S3.
     *
     * @param key The S3 object key of the file to delete
     */
    void deleteFile(String key);

    /**
     * Cleans up temporary files and directories.
     *
     * @param tempPath Path to the temporary file to clean up
     */
    void cleanup(Path tempPath);
}