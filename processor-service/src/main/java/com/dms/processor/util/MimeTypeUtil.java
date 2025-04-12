package com.dms.processor.util;

import com.dms.processor.enums.DocumentType;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for MIME type operations.
 */
@Slf4j
public class MimeTypeUtil {

    private static final Map<String, String> EXTENSION_TO_MIME_TYPE = new HashMap<>();

    static {
        // Microsoft Office formats
        EXTENSION_TO_MIME_TYPE.put("doc", "application/msword");
        EXTENSION_TO_MIME_TYPE.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        EXTENSION_TO_MIME_TYPE.put("xls", "application/vnd.ms-excel");
        EXTENSION_TO_MIME_TYPE.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        EXTENSION_TO_MIME_TYPE.put("ppt", "application/vnd.ms-powerpoint");
        EXTENSION_TO_MIME_TYPE.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        // PDF
        EXTENSION_TO_MIME_TYPE.put("pdf", "application/pdf");

        // Text formats
        EXTENSION_TO_MIME_TYPE.put("txt", "text/plain");
        EXTENSION_TO_MIME_TYPE.put("csv", "text/csv");
        EXTENSION_TO_MIME_TYPE.put("xml", "application/xml");
        EXTENSION_TO_MIME_TYPE.put("json", "application/json");
        EXTENSION_TO_MIME_TYPE.put("md", "text/markdown");
        EXTENSION_TO_MIME_TYPE.put("rtf", "application/rtf");

        // Other common formats
        EXTENSION_TO_MIME_TYPE.put("html", "text/html");
        EXTENSION_TO_MIME_TYPE.put("htm", "text/html");
        EXTENSION_TO_MIME_TYPE.put("zip", "application/zip");
        EXTENSION_TO_MIME_TYPE.put("tar", "application/x-tar");
        EXTENSION_TO_MIME_TYPE.put("gz", "application/gzip");
    }

    /**
     * Get MIME type from file extension.
     *
     * @param filePath the path to the file
     * @return the MIME type or null if not found
     */
    public static String getMimeTypeFromExtension(Path filePath) {
        if (filePath == null) {
            return null;
        }

        String fileName = filePath.getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            String extension = fileName.substring(lastDotIndex + 1).toLowerCase();
            return EXTENSION_TO_MIME_TYPE.get(extension);
        }

        return null;
    }

    /**
     * Check if the file has a supported extension.
     *
     * @param filePath the path to check
     * @return true if the file has a supported extension
     */
    public static boolean hasSupportedExtension(Path filePath) {
        String mimeType = getMimeTypeFromExtension(filePath);
        return mimeType != null && DocumentType.isSupportedMimeType(mimeType);
    }

    /**
     * Get the file extension from a path.
     *
     * @param filePath the path to extract from
     * @return the extension or empty string if none found
     */
    public static String getFileExtension(Path filePath) {
        if (filePath == null) {
            return "";
        }

        String fileName = filePath.getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }

        return "";
    }

    /**
     * Resolves special characters in the file path that might cause issues with MIME type detection.
     *
     * @param filePath the original file path
     * @return a sanitized file path with special characters replaced, or the original if no issues found
     */
    public static Path sanitizeFilePathForMimeDetection(Path filePath) {
        if (filePath == null) {
            return null;
        }

        String fileName = filePath.getFileName().toString();

        // Check if filename contains problematic characters
        if (fileName.matches(".*[@#$%^&*()\\[\\]{}|;:,<>?].*")) {
            log.debug("Filename contains special characters that might affect MIME detection: {}", fileName);

            // Create a temporary clean name by replacing special chars with underscores
            String cleanName = fileName.replaceAll("[@#$%^&*()\\[\\]{}|;:,<>?]", "_");

            // Create a sanitized path (note: this doesn't change the actual file on disk)
            return filePath.getParent().resolve(cleanName);
        }

        return filePath;
    }
}