package com.dms.processor.service.extraction;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.dto.ExtractedText;
import com.dms.processor.dto.TextMetrics;
import com.dms.processor.exception.DocumentProcessingException;
import com.dms.processor.service.OcrService;
import com.dms.processor.service.impl.ContentQualityAnalyzer;
import com.dms.processor.service.impl.LargeFileProcessor;
import com.dms.processor.service.impl.OcrLargeFileProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

    private final ContentQualityAnalyzer contentQualityAnalyzer;
    private final LargeFileProcessor largeFileProcessor;
    private final OcrLargeFileProcessor ocrLargeFileProcessor;
    private final MetadataExtractor metadataExtractor;
    private final OcrService ocrService;

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
                ExtractedText result = extractTextPdfFile(filePath);
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
     * Extracts text from a PDF file, using OCR only when necessary.
     *
     * @param pdfPath Path to the PDF file
     * @return Extracted text and information about whether OCR was used
     */
    public ExtractedText extractTextPdfFile(Path pdfPath) throws IOException, TesseractException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            int pageCount = document.getNumberOfPages();

            // Extract text and calculate metrics
            PDFTextStripper textStripper = new PDFTextStripper();
            String pdfText = textStripper.getText(document);

            TextMetrics metrics = contentQualityAnalyzer.analyzeTextQuality(pdfText, pageCount);
            log.debug("PDF metrics - Density: {}, Quality: {}, HasMeaningfulText: {}",
                    metrics.getTextDensity(), metrics.getTextQuality(), metrics.isHasMeaningfulText());

            // Decide whether to use OCR based on metrics and text length
            if (!contentQualityAnalyzer.shouldUseOcr(metrics, pdfText)) {
                log.info("Using PDFTextStripper - Density: {}, Quality: {}",
                        metrics.getTextDensity(), metrics.getTextQuality());
                return new ExtractedText(pdfText, false);
            }

            // Use OCR if needed
            log.info("Using OCR - Low text metrics - Density: {}, Quality: {}",
                    metrics.getTextDensity(), metrics.getTextQuality());

            // Use OCR with Tesseract
            String ocrText = ocrService.processWithOcr(pdfPath, document.getNumberOfPages());
            return new ExtractedText(ocrText, true);
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
            // Analyze only the first few pages
            TextMetrics metrics = contentQualityAnalyzer.calculateMetricsForSample(pdfPath, sampleSizePages);
            return contentQualityAnalyzer.shouldUseOcr(metrics, "");
        } catch (Exception e) {
            log.error("Error sampling PDF for OCR decision", e);
            // Default to true (use OCR) in case of error
            return true;
        }
    }
}