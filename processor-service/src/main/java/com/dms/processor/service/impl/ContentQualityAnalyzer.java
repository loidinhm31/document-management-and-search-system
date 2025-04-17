package com.dms.processor.service.impl;

import com.dms.processor.dto.ExtractedText;
import com.dms.processor.dto.TextMetrics;
import com.dms.processor.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Service that analyzes content quality and makes decisions about text extraction strategies.
 * It handles the assessment of text quality for all document types and determines
 * when OCR is necessary based on quality metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentQualityAnalyzer {

    private final OcrService ocrService;

    @Value("${app.pdf.quality-threshold:0.8}")
    private double qualityThreshold;

    @Value("${app.pdf.min-text-density:0.01}")
    private double minTextDensity;

    @Value("${app.pdf.expected-min-chars-per-page:250}")
    private double expectedMinCharsPerPage;

    @Value("${app.pdf.minimum-text-length:50}")
    private int minimumTextLength;

    // Pattern to detect meaningful text (more sophisticated than simple char counting)
    private static final Pattern MEANINGFUL_TEXT_PATTERN =
            Pattern.compile("[a-zA-Z]{2,}\\s+([a-zA-Z]{2,}\\s+){2,}");

    /**
     * Calculates text quality metrics for a sample of pages from a PDF.
     */
    public TextMetrics calculateMetricsForSample(Path pdfPath, int samplePages) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            int pageCount = document.getNumberOfPages();
            int pagesToCheck = Math.min(samplePages, pageCount);

            // Only extract text from the first few pages as a sample
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setStartPage(1);
            textStripper.setEndPage(pagesToCheck);
            String sampleText = textStripper.getText(document);

            // Calculate metrics based on the sample
            return calculateTextMetrics(sampleText, pagesToCheck);
        }
    }

    public TextMetrics calculateTextMetrics(String text, int pageCount) {
        double textDensity = calculateTextDensity(text, pageCount);
        double textQuality = assessTextQuality(text);
        boolean hasMeaningfulText = detectMeaningfulText(text);

        return new TextMetrics(textDensity, textQuality, hasMeaningfulText);
    }

    protected double calculateTextDensity(String text, int pageCount) {
        if (pageCount == 0) return 0;

        // Calculate characters per page
        double charsPerPage = (double) text.length() / pageCount;

        // Normalize against expected minimum chars per page
        return Math.min(charsPerPage / expectedMinCharsPerPage, 1.0);
    }

    protected double assessTextQuality(String text) {
        if (text == null || text.isEmpty()) return 0;

        // Count recognizable characters vs total
        int totalChars = text.length();
        int recognizableChars = text.replaceAll("[^a-zA-Z0-9\\s.,;:!?()\\[\\]{}\"'`-]", "").length();

        return (double) recognizableChars / totalChars;
    }

    protected boolean detectMeaningfulText(String text) {
        // Check if text contains meaningful sequences of words
        return MEANINGFUL_TEXT_PATTERN.matcher(text).find();
    }

    /**
     * Determines if OCR should be used based on text metrics
     * Implemented from TextQualityAnalyzer interface for reuse by other components
     */
    public boolean shouldUseOcr(TextMetrics metrics, String text) {
        // First check if content have enough text at all
        if (text == null || text.trim().length() < minimumTextLength) {
            return true;
        }

        // Check meaningful text detection
        return !metrics.isHasMeaningfulText() ||
               !(metrics.getTextDensity() >= minTextDensity) ||
               !(metrics.getTextQuality() >= qualityThreshold);
    }

    /**
     * Analyze text quality for any type of content, not just PDFs
     * Allows reuse of this logic for non-PDF documents
     */
    public TextMetrics analyzeTextQuality(String text, int estimatedPages) {
        double textDensity = calculateTextDensity(text, estimatedPages);
        double textQuality = assessTextQuality(text);
        boolean hasMeaningfulText = detectMeaningfulText(text);

        return new TextMetrics(textDensity, textQuality, hasMeaningfulText);
    }
}