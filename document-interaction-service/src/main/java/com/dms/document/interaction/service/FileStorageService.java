package com.dms.document.interaction.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {

    /**
     * Uploads a file to the configured storage
     *
     * @param file the file to upload
     * @param prefix the prefix to prepend to the file path/key
     * @return the file path or key of the uploaded file
     * @throws IOException if the upload fails
     */
    String uploadFile(MultipartFile file, String prefix) throws IOException;

    /**
     * Downloads a file from the configured storage
     *
     * @param filePathOrKey the file path or key of the file to download
     * @return the file content as a byte array
     * @throws IOException if the download fails
     */
    byte[] downloadFile(String filePathOrKey) throws IOException;

    /**
     * Deletes a file from the configured storage
     *
     * @param filePathOrKey the file path or key of the file to delete
     */
    void deleteFile(String filePathOrKey);
}