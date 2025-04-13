package com.dms.processor.service.impl;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.dto.ExtractedText;
import com.dms.processor.dto.TextMetrics;
import com.dms.processor.enums.DocumentType;
import com.dms.processor.service.ContentExtractorService;
import com.dms.processor.service.OcrService;
import com.dms.processor.service.TextQualityAnalyzer;
import com.dms.processor.util.MimeTypeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.sax.ToTextContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.xml.sax.ContentHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentExtractorServiceImpl implements ContentExtractorService {

    @Value("${app.ocr.large-size-threshold-mb}")
    private DataSize largeSizeThreshold;

    @Value("${app.ocr.sample-size-pages:5}")
    private int sampleSizePages;

    private static final Set<String> OCR_CANDIDATE_MIME_TYPES = Set.of(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final AutoDetectParser parser = new AutoDetectParser();
    private final ContentQualityAnalyzer contentQualityAnalyzer;
    private final OcrLargeFileProcessor ocrLargeFileProcessor;
    private final LargeFileProcessor largeFileProcessor;
    private final TextQualityAnalyzer textQualityAnalyzer;
    private final OcrService ocrService;

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

            // Initialize metadata map
            Map<String, String> metadata = new HashMap<>();
            metadata.put("Detected-MIME-Type", mimeType);

            // Use DocumentType enum to get the document type display name
            try {
                DocumentType docType = DocumentType.fromMimeType(mimeType);
                metadata.put("Document-Type", docType.name());
                metadata.put("Document-Type-Display", docType.getDisplayName());
                log.info("Document type detected: {} ({})", docType.name(), docType.getDisplayName());
            } catch (Exception e) {
                log.warn("Could not determine document type from MIME type: {}", mimeType);
                metadata.put("Document-Type", "UNKNOWN");
            }

            // Now proceed with the extraction based on the detected MIME type
            String extractedText;

            // Check file size
            long fileSize = Files.size(filePath);
            boolean isLargeFile = fileSize > largeSizeThreshold.toBytes();

            if ("application/pdf".equals(mimeType)) {
                extractedText = handlePdfExtraction(filePath, metadata, isLargeFile);
                return new DocumentExtractContent(extractedText, metadata);
            } else {
                // For non-PDF files, check if this is a file type that might benefit from OCR
                boolean shouldConsiderOcr = shouldConsiderOcr(mimeType);

                if (isLargeFile) {
                    // For large non-PDF files
                    extractedText = handleLargeNonPdfFile(filePath, mimeType, shouldConsiderOcr);
                } else {
                    // For regular sized non-PDF files
                    extractedText = handleRegularNonPdfFile(filePath, mimeType, shouldConsiderOcr);
                }

                // Even for large files, try to extract metadata
                metadata.putAll(extractMetadata(filePath));
                return new DocumentExtractContent(extractedText, metadata);
            }
        } catch (Exception e) {
            log.error("Error extracting content from file: {}", filePath, e);
            return new DocumentExtractContent("", new HashMap<>());
        }
    }

    private String determineMimeType(Path filePath, Path sanitizedPath) throws IOException {
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

    /**
     * Handles extraction of content from PDF files
     */
    private String handlePdfExtraction(Path filePath, Map<String, String> metadata, boolean isLargeFile) throws IOException, TesseractException, ExecutionException, InterruptedException, TimeoutException {
        long fileSize = Files.size(filePath);
        long fileSizeInMb = fileSize / (1024 * 1024);
        metadata.put("File-Size-MB", String.valueOf(fileSizeInMb));

        // Extract PDF-specific metadata
        Map<String, String> pdfMetadata = extractMetadata(filePath);
        metadata.putAll(pdfMetadata);

        log.info("File size: ({}MB)", fileSizeInMb);

        if (isLargeFile) {
            log.info("Large PDF detected ({}MB). Sampling first to determine processing method.", fileSizeInMb);

            // For large PDFs, first sample to determine if OCR is needed
            if (shouldUseOcrForLargePdf(filePath)) {
                log.info("Sample suggests OCR is needed for large PDF");
                metadata.put("Processing-Method", "chunked-ocr");
                return ocrLargeFileProcessor.processLargeFile(filePath);
            } else {
                log.info("Sample suggests direct text extraction for large PDF");
                metadata.put("Processing-Method", "chunked-extract");

                // Use LargeFileProcessor with direct text extraction instead
                CompletableFuture<String> future = largeFileProcessor.processLargeFile(filePath);
                return future.get(30, TimeUnit.MINUTES);
            }
        }

        log.info("Processing regular PDF ({}MB)", fileSizeInMb);
        ExtractedText result = contentQualityAnalyzer.extractText(filePath);
        metadata.put("Processing-Method", result.usedOcr() ? "ocr" : "direct");
        metadata.put("Used-OCR", String.valueOf(result.usedOcr()));

        return result.text();
    }

    /**
     * Handles extraction of content from large non-PDF files
     */
    private String handleLargeNonPdfFile(Path filePath, String mimeType, boolean shouldConsiderOcr) throws Exception {
        if (shouldConsiderOcr) {
            log.info("Large file with MIME type {} might benefit from OCR. Sampling first.", mimeType);

            // For files where OCR might be beneficial, first check a sample
            if (sampleNonPdfFileForOcr(filePath, mimeType)) {
                log.info("Sample suggests OCR for large non-PDF file");
                // Use the OcrLargeFileProcessor
                return ocrLargeFileProcessor.processLargeFile(filePath);
            }
        }

        // Use regular large file processing
        log.info("Using standard large file processing");
        CompletableFuture<String> future = largeFileProcessor.processLargeFile(filePath);
        return future.get(30, TimeUnit.MINUTES);
    }

    /**
     * Handles extraction of content from regular sized non-PDF files
     */
    private String handleRegularNonPdfFile(Path filePath, String mimeType, boolean shouldConsiderOcr) throws Exception {
        String result;

        // For files that might contain images with text (presentations, etc.)
        if (shouldConsiderOcr) {
            // Use Tika with OCR enabled
            result = tikaExtractTextContent(filePath, new HashMap<>(), false);

            int estimatedPages = 1; // Default for small files
            TextMetrics metrics = textQualityAnalyzer.analyzeTextQuality(result, estimatedPages);

            // If result quality is poor, try OCR directly
            if (textQualityAnalyzer.shouldUseOcr(metrics, result)) {
                log.info("Initial extraction yielded poor results, trying OCR for {}", mimeType);
                result = ocrService.extractTextFromNonPdf(filePath);
            }
        } else {
            // Use Tika with OCR disabled
            result = tikaExtractTextContent(filePath, new HashMap<>(), true);
        }

        // Fallback to basic text extraction if tika cannot handle
        if (StringUtils.isBlank(result)) {
            result = basicExtractTextContent(filePath);
        }

        return result;
    }

    /**
     * Determines if a file type should be considered for OCR processing
     */
    private boolean shouldConsiderOcr(String mimeType) {
        return OCR_CANDIDATE_MIME_TYPES.contains(mimeType);
    }

    /**
     * Samples a PDF to determine if OCR is needed
     */
    private boolean shouldUseOcrForLargePdf(Path pdfPath) {
        try {
            // Use ContentQualityAnalyzer to analyze only the first few pages
            TextMetrics metrics = contentQualityAnalyzer.calculateMetricsForSample(pdfPath, sampleSizePages);
            // Use the consistent TextQualityAnalyzer interface for the decision
            return textQualityAnalyzer.shouldUseOcr(metrics, "");
        } catch (Exception e) {
            log.error("Error sampling PDF for OCR decision", e);
            // Default to true (use OCR) in case of error
            return true;
        }
    }

    /**
     * Samples a non-PDF file to determine if OCR might be beneficial
     */
    private boolean sampleNonPdfFileForOcr(Path filePath, String mimeType) {
        try {
            // First try normal text extraction on a sample
            String extractedSample = tikaExtractTextContent(filePath, new HashMap<>(), true);

            // Use the same quality metrics as for PDFs
            if (StringUtils.isBlank(extractedSample)) {
                return true;
            }

            // Estimate number of pages based on content size (rough heuristic)
            int estimatedPages = Math.max(1, extractedSample.length() / 3000);

            // Use the same quality analysis as for PDFs
            TextMetrics metrics = textQualityAnalyzer.analyzeTextQuality(extractedSample, estimatedPages);
            return textQualityAnalyzer.shouldUseOcr(metrics, extractedSample);
        } catch (Exception e) {
            log.error("Error sampling file for OCR decision", e);
            // Default to false for non-PDFs
            return false;
        }
    }

    protected String basicExtractTextContent(Path filePath) {
        // Use larger buffer size for better performance with large files
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath.toFile()), StandardCharsets.UTF_8),
                8192 * 4)) { // 32KB buffer

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

    String tikaExtractTextContent(Path filePath, Map<String, String> metadata, boolean skipOcr) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(filePath))) {
            StringWriter writer = new StringWriter();
            ContentHandler handler = new ToTextContentHandler(writer); // Extract text without format
            Metadata tikaMetadata = new Metadata();
            ParseContext context = new ParseContext();

            // Configure Tesseract OCR settings
            TesseractOCRConfig config = new TesseractOCRConfig();
            config.setSkipOcr(skipOcr);

            // If not skipping OCR, configure OCR parameters
            if (!skipOcr) {
                config.setLanguage("eng+vie");
                config.setEnableImagePreprocessing(true);
            }

            context.set(TesseractOCRConfig.class, config);

            parser.parse(input, handler, tikaMetadata, context);

            // Extract and add metadata to the provided map
            extractMetadataFromTika(tikaMetadata, metadata);

            return writer.toString();
        } catch (Exception e) {
            log.error("Error extracting text content", e);
            return "";
        }
    }

    private Map<String, String> extractMetadata(Path filePath) {
        Map<String, String> extractedMetadata = new HashMap<>();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(filePath))) {
            Metadata tikaMetadata = new Metadata();
            ContentHandler handler = new ToTextContentHandler();
            ParseContext context = new ParseContext();

            // Add the filename to metadata
            tikaMetadata.set("resourceName", filePath.getFileName().toString());

            // Parse the document to extract metadata
            parser.parse(input, handler, tikaMetadata, context);

            // Extract and filter metadata
            extractMetadataFromTika(tikaMetadata, extractedMetadata);

        } catch (Exception e) {
            log.error("Error extracting metadata from file: {}", filePath, e);
        }
        return extractedMetadata;
    }

    private void extractMetadataFromTika(Metadata tikaMetadata, Map<String, String> targetMap) {
        for (String name : tikaMetadata.names()) {
            if (isImportantMetadata(name)) {
                String value = tikaMetadata.get(name);
                if (value != null && !value.isEmpty()) {
                    // Store with a cleaner key name
                    String cleanName = name.replaceAll("^.*:", "").trim();
                    targetMap.put(cleanName, value);
                }
            }
        }
    }

    protected boolean isImportantMetadata(String name) {
        // Define important metadata keys
        Set<String> importantKeys = Set.of(
                // Common metadata
                "Content-Type",
                "Last-Modified",
                "Creation-Date",
                "Author",
                "Producer",
                "Creator",
                "Created",
                "Modified",
                "Title",
                "Subject",
                "Description",
                "Keywords",
                "Publisher",

                // Document statistics
                "Page-Count",
                "Word-Count",
                "Character-Count",
                "Paragraph-Count",
                "Line-Count",
                "Slide-Count",

                // PDF specific
                "pdf:PDFVersion",
                "pdf:docinfo:created",
                "pdf:docinfo:creator",
                "pdf:docinfo:producer",
                "pdf:docinfo:modified",
                "xmpTPg:NPages",

                // Office specific
                "Application-Name",
                "Application-Version",
                "Manager",
                "Company",
                "Revision-Number",
                "Template",
                "Total-Time",
                "Edit-Time"
        );

        // Check for direct matches
        if (importantKeys.contains(name)) {
            return true;
        }

        // Check for partial matches with key metadata words
        String lowerName = name.toLowerCase();
        return lowerName.contains("creator") ||
               lowerName.contains("created") ||
               lowerName.contains("modified") ||
               lowerName.contains("author") ||
               lowerName.contains("producer") ||
               lowerName.contains("title") ||
               lowerName.contains("subject") ||
               lowerName.contains("count") ||
               lowerName.contains("pages") ||
               lowerName.contains("version");
    }
}