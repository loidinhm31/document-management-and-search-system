package com.dms.processor.service.extraction;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.dto.ExtractedText;
import com.dms.processor.dto.TextMetrics;
import com.dms.processor.exception.DocumentProcessingException;
import com.dms.processor.service.TextQualityAnalyzer;
import com.dms.processor.service.impl.LargeFileProcessor;
import com.dms.processor.service.impl.OcrLargeFileProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Extraction strategy for PDF documents.
 */
@Component
@Order(10) // High priority for PDF files
@RequiredArgsConstructor
@Slf4j
public class PdfExtractionStrategy implements DocumentExtractionStrategy {

    private final ContentQualityAnalysis contentQualityAnalysis;
    private final TextQualityAnalyzer textQualityAnalyzer;
    private final LargeFileProcessor largeFileProcessor;
    private final OcrLargeFileProcessor ocrLargeFileProcessor;
    private final MetadataExtractor metadataExtractor;

    @Value("${app.ocr.large-size-threshold-mb}")
    private DataSize largeSizeThreshold;

    @Value("${app.ocr.sample-size-pages:5}")
    private int sampleSizePages;

    @Override
    public boolean canHandle(Path filePath, String mimeType) {
        return "application/pdf".equals(mimeType);
    }

    @Override
    public DocumentExtractContent extract(Path filePath, String mimeType, Map<String, String> metadata) throws DocumentProcessingException {
        log.info("Using pdf extraction strategy for file: {} with MIME type: {}",
                filePath.getFileName(), mimeType);
        try {
            metadata.put("Detected-MIME-Type", mimeType);
            metadata.put("Document-Type", "PDF");
            metadata.put("Document-Type-Display", "PDF Document");

            // Extract file size and add to metadata
            long fileSize = Files.size(filePath);
            long fileSizeInMb = fileSize / (1024 * 1024);
            metadata.put("File-Size-MB", String.valueOf(fileSizeInMb));

            // Extract PDF-specific metadata
            metadata.putAll(metadataExtractor.extractMetadata(filePath));

            log.info("Processing PDF file: {} ({}MB)", filePath.getFileName(), fileSizeInMb);

            // Choose appropriate processing method based on file size
            String extractedText;
            boolean isLargeFile = fileSize > largeSizeThreshold.toBytes();

            if (isLargeFile) {
                extractedText = processLargePdf(filePath, metadata);
            } else {
                ExtractedText result = contentQualityAnalysis.extractText(filePath);
                metadata.put("Processing-Method", result.usedOcr() ? "ocr" : "direct");
                metadata.put("Used-OCR", String.valueOf(result.usedOcr()));
                extractedText = result.text();
            }

            return new DocumentExtractContent(extractedText, metadata);
        } catch (Exception e) {
            log.error("Error extracting content from PDF: {}", filePath, e);
            throw new DocumentProcessingException("Failed to extract content from PDF", e);
        }
    }

    /**
     * Processes a large PDF file by first sampling to determine the best extraction method.
     */
    private String processLargePdf(Path filePath, Map<String, String> metadata)
            throws IOException, ExecutionException, InterruptedException, TimeoutException, TesseractException {
        log.info("Large PDF detected. Sampling first to determine processing method.");

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

    /**
     * Determines if OCR should be used for a large PDF based on a sample.
     */
    private boolean shouldUseOcrForLargePdf(Path pdfPath) {
        try {
            // Use ContentQualityAnalysis to analyze only the first few pages
            TextMetrics metrics = contentQualityAnalysis.calculateMetricsForSample(pdfPath, sampleSizePages);
            // Use the TextQualityAnalyzer interface for the decision
            return textQualityAnalyzer.shouldUseOcr(metrics, "");
        } catch (Exception e) {
            log.error("Error sampling PDF for OCR decision", e);
            // Default to true (use OCR) in case of error
            return true;
        }
    }
}