package com.dms.processor.service;

import com.dms.processor.enums.DocumentType;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Service interface for generating thumbnails from various document types
 */
public interface ThumbnailService {

    /**
     * Generates a thumbnail image from the provided file
     *
     * @param filePath The path to the document file
     * @param documentType The type of document (PDF, WORD, etc.)
     * @param content The text content of the document (used for certain document types)
     * @return Byte array containing the thumbnail image data in PNG format
     * @throws IOException If there's an error processing the file
     */
    byte[] generateThumbnail(Path filePath, DocumentType documentType, String content) throws IOException;
}