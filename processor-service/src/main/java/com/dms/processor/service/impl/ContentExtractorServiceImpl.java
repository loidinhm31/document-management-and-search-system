package com.dms.processor.service.impl;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.enums.DocumentType;
import com.dms.processor.exception.DocumentProcessingException;
import com.dms.processor.service.ContentExtractorService;
import com.dms.processor.service.extraction.DocumentExtractorFactory;
import com.dms.processor.util.MimeTypeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;


@Service
@Slf4j
@RequiredArgsConstructor
public class ContentExtractorServiceImpl implements ContentExtractorService {

    private final DocumentExtractorFactory extractorFactory;

    @Override
    public DocumentExtractContent extractContent(Path filePath) {
        try {
            // First check if file exists and is readable
            if (!Files.exists(filePath)) {
                log.error("File does not exist: {}", filePath);
                return new DocumentExtractContent("", new HashMap<>());
            }

            if (!Files.isReadable(filePath)) {
                log.error("File is not readable: {}", filePath);
                return new DocumentExtractContent("", new HashMap<>());
            }

            // Check if the filename contains special characters that might affect MIME detection
            Path sanitizedPath = MimeTypeUtil.sanitizeFilePathForMimeDetection(filePath);
            if (!sanitizedPath.equals(filePath)) {
                log.info("Sanitized file path for MIME detection from '{}' to '{}'",
                        filePath.getFileName(), sanitizedPath.getFileName());
            }

            // Try to determine MIME type with multiple methods
            String mimeType = determineMimeType(filePath, sanitizedPath);
            log.info("Detected MIME type: {} for file: {}", mimeType, filePath.getFileName());

            // Initialize base metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("Detected-MIME-Type", mimeType);

            // Add document type information to metadata
            try {
                DocumentType docType = DocumentType.fromMimeType(mimeType);
                metadata.put("Document-Type", docType.name());
                metadata.put("Document-Type-Display", docType.getDisplayName());
                log.info("Document type detected: {} ({})", docType.name(), docType.getDisplayName());
            } catch (Exception e) {
                log.warn("Could not determine document type from MIME type: {}", mimeType);
                metadata.put("Document-Type", "UNKNOWN");
            }

            // Get the appropriate strategy from the factory
            return extractorFactory.getStrategy(filePath, mimeType)
                    .extract(filePath, mimeType, metadata);

        } catch (Exception e) {
            log.error("Error extracting content from file: {}", filePath, e);
            return new DocumentExtractContent("", new HashMap<>());
        }
    }


    /**
     * Determines the MIME type of file using multiple methods.
     */
    private String determineMimeType(Path filePath, Path sanitizedPath) throws java.io.IOException {
        String mimeType = null;

        // Try standard Files.probeContentType with both original and sanitized paths
        try {
            mimeType = Files.probeContentType(filePath);
            log.debug("MIME type from probeContentType (original path): {}", mimeType);

            // If failed with original path but we have a sanitized path, try that
            if (mimeType == null && !sanitizedPath.equals(filePath)) {
                mimeType = Files.probeContentType(sanitizedPath);
                log.debug("MIME type from probeContentType (sanitized path): {}", mimeType);
            }
        } catch (Exception e) {
            log.warn("Error using probeContentType: {}", e.getMessage());
        }

        // Use MimeTypeUtil to determine MIME type from extension
        if (mimeType == null) {
            log.debug("Trying to determine MIME type from file extension for: {}", filePath.getFileName());
            mimeType = MimeTypeUtil.getMimeTypeFromExtension(filePath);

            if (mimeType != null) {
                log.info("Determined MIME type from file extension: {}", mimeType);
            }
        }

        // Use Apache Tika for more accurate detection if still null
        if (mimeType == null) {
            try {
                org.apache.tika.Tika tika = new org.apache.tika.Tika();
                mimeType = tika.detect(filePath.toFile());
                log.info("MIME type detected by Tika: {}", mimeType);
            } catch (Exception e) {
                log.warn("Error using Tika for MIME detection: {}", e.getMessage());
            }
        }

        // If still null, log detailed file information and fall back to a generic type
        if (mimeType == null) {
            log.warn("Could not determine mime type for file: {}", filePath);
            log.warn("File details - Exists: {}, Readable: {}, Size: {} bytes, Hidden: {}",
                    Files.exists(filePath),
                    Files.isReadable(filePath),
                    Files.exists(filePath) ? Files.size(filePath) : -1,
                    Files.exists(filePath) && Files.isHidden(filePath));

            // For .docx files, use a hardcoded fallback
            if (filePath.toString().toLowerCase().endsWith(".docx")) {
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                log.info("Using fallback MIME type for .docx file: {}", mimeType);
            } else {
                // Default to a generic type that Tika can still attempt to process
                mimeType = "application/octet-stream";
                log.info("Using fallback generic MIME type: {}", mimeType);
            }
        }

        return mimeType;
    }
}