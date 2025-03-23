package com.dms.document.interaction.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface S3Service {
    /**
     * Uploads a file to S3 storage
     *
     * @param file the file to upload
     * @param prefix the prefix to prepend to the S3 key
     * @return the S3 key of the uploaded file
     * @throws IOException if the upload fails
     */
    String uploadFile(MultipartFile file, String prefix) throws IOException;

    /**
     * Downloads a file from S3 storage
     *
     * @param key the S3 key of the file to download
     * @return the file content as a byte array
     * @throws IOException if the download fails
     */
    byte[] downloadFile(String key) throws IOException;

    /**
     * Deletes a file from S3 storage
     *
     * @param key the S3 key of the file to delete
     */
    void deleteFile(String key);
}