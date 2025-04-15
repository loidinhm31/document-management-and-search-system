package com.dms.processor.service.extraction;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.dto.TextMetrics;
import com.dms.processor.exception.DocumentProcessingException;
import com.dms.processor.service.OcrService;
import com.dms.processor.service.impl.ContentQualityAnalyzer;
import com.dms.processor.service.impl.LargeFileProcessor;
import com.dms.processor.service.impl.OcrLargeFileProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Extraction strategy for regular documents.
 */
@Component
@Order(20) // Medium priority for regular files
@RequiredArgsConstructor
@Slf4j
public class RegularExtractionStrategy implements DocumentExtractionStrategy {

    private final TikaExtractor tikaExtractor;
    private final OcrService ocrService;
    private final ContentQualityAnalyzer contentQualityAnalyzer;
    private final LargeFileProcessor largeFileProcessor;
    private final OcrLargeFileProcessor ocrLargeFileProcessor;
    private final MetadataExtractor metadataExtractor;

    @Value("${app.ocr.large-size-threshold-mb}")
    private DataSize largeSizeThreshold;

    private static final Set<String> OCR_CANDIDATE_MIME_TYPES = Set.of(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    @Override
    public boolean canHandle(Path filePath, String mimeType) {
        return !"application/pdf".equals(mimeType);
    }

    @Override
    public DocumentExtractContent extract(Path filePath, String mimeType, Map<String, String> metadata) throws DocumentProcessingException {
        log.info("Using regular extraction strategy for file: {} with MIME type: {}",
                filePath.getFileName(), mimeType);
        try {
            // Check file size
            long fileSize = Files.size(filePath);
            boolean isLargeFile = fileSize > largeSizeThreshold.toBytes();

            // Determine if this file type might benefit from OCR
            boolean shouldConsiderOcr = shouldConsiderOcr(mimeType);

            String extractedText;

            if (isLargeFile) {
                extractedText = handleLargeRegularFile(filePath, mimeType, shouldConsiderOcr);
            } else {
                extractedText = handleRegularFile(filePath, mimeType, shouldConsiderOcr);
            }

            // Extract metadata even for large files
            metadata.putAll(metadataExtractor.extractMetadata(filePath));

            return new DocumentExtractContent(extractedText, metadata);
        } catch (Exception e) {
            log.error("Error extracting content from regular file: {}", filePath, e);
            throw new DocumentProcessingException("Failed to extract content from regular file", e);
        }
    }

    /**
     * Handles extraction from large regular files.
     */
    private String handleLargeRegularFile(Path filePath, String mimeType, boolean shouldConsiderOcr) throws Exception {
        if (shouldConsiderOcr) {
            log.info("Large file with MIME type {} might benefit from OCR. Sampling first.", mimeType);

            // For files where OCR might be beneficial, first check a sample
            if (sampleRegularFileForOcr(filePath, mimeType)) {
                log.info("Sample suggests OCR for large regular file");
                // Use the OcrLargeFileProcessor
                return ocrLargeFileProcessor.processLargeFile(filePath);
            }
        }

        // Use regular large file processing
        log.info("Using standard large file processing");
        CompletableFuture<String> future = largeFileProcessor.processLargeFile(filePath);
        return future.get();
    }

    /**
     * Handles extraction from regular sized regular files.
     */
    private String handleRegularFile(Path filePath, String mimeType, boolean shouldConsiderOcr) throws Exception {
        String result;
        String tikaBackupResult = null;

        // For files that might contain images with text (presentations, etc.)
        if (shouldConsiderOcr) {
            // Use Tika with OCR enabled
            result = tikaExtractor.extractTextContent(filePath, new HashMap<>(), false);
            tikaBackupResult = result;

            int estimatedPages = 1; // Default for small files
            TextMetrics metrics = contentQualityAnalyzer.analyzeTextQuality(result, estimatedPages);

            // If result quality is poor, try OCR directly
            if (contentQualityAnalyzer.shouldUseOcr(metrics, result)) {
                log.info("Initial extraction yielded poor results, trying OCR for {}", mimeType);
                result = ocrService.extractTextFromRegularFile(filePath);
            }
        } else {
            // Use Tika with OCR disabled
            result = tikaExtractor.extractTextContent(filePath, new HashMap<>(), true);
        }

        // Fallback to basic text extraction if tika cannot handle
        if (StringUtils.isBlank(result)) {
            result = basicExtractTextContent(filePath);
        }

        return StringUtils.isBlank(result) ? tikaBackupResult : result;
    }

    /**
     * Determines if a file type should be considered for OCR processing.
     */
    private boolean shouldConsiderOcr(String mimeType) {
        return OCR_CANDIDATE_MIME_TYPES.contains(mimeType);
    }

    /**
     * Samples a regular file to determine if OCR might be beneficial.
     */
    private boolean sampleRegularFileForOcr(Path filePath, String mimeType) {
        try {
            // First try normal text extraction on a sample
            String extractedSample = tikaExtractor.extractTextContent(filePath, new HashMap<>(), true);

            // Use the same quality metrics as for PDFs
            if (StringUtils.isBlank(extractedSample)) {
                return true;
            }

            // Estimate number of pages based on content size (rough heuristic)
            int estimatedPages = Math.max(1, extractedSample.length() / 3000);

            // Use the same quality analysis as for PDFs
            TextMetrics metrics = contentQualityAnalyzer.analyzeTextQuality(extractedSample, estimatedPages);
            return contentQualityAnalyzer.shouldUseOcr(metrics, extractedSample);
        } catch (Exception e) {
            log.error("Error sampling file for OCR decision", e);
            // Default to false for regular files
            return false;
        }
    }

    /**
     * Basic text extraction as a fallback method.
     */
    protected String basicExtractTextContent(Path filePath) {
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[8192]; // 8KB chunks
            int numChars;

            while ((numChars = reader.read(buffer)) != -1) {
                content.append(buffer, 0, numChars);
            }

            return content.toString();
        } catch (IOException e) {
            log.error("Error reading file content from {}: {}", filePath, e.getMessage());
            return "";
        }
    }
}