package com.dms.processor.service.impl;

import com.dms.processor.dto.ExtractedText;
import com.dms.processor.dto.PdfTextMetrics;
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


@Service
@RequiredArgsConstructor
@Slf4j
public class SmartPdfExtractor {

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

    public ExtractedText extractText(Path pdfPath) throws IOException, TesseractException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            int pageCount = document.getNumberOfPages();

            // Extract text and calculate metrics
            PDFTextStripper textStripper = new PDFTextStripper();
            String pdfText = textStripper.getText(document);

            PdfTextMetrics metrics = calculateTextMetrics(pdfText, pageCount);
            log.debug("PDF metrics - Density: {}, Quality: {}, HasMeaningfulText: {}",
                    metrics.getTextDensity(), metrics.getTextQuality(), metrics.isHasMeaningfulText());

            // Decide whether to use OCR based on metrics and text length
            if (!shouldUseOcr(metrics, pdfText)) {
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

    private PdfTextMetrics calculateTextMetrics(String text, int pageCount) {
        double textDensity = calculateTextDensity(text, pageCount);
        double textQuality = assessTextQuality(text);
        boolean hasMeaningfulText = detectMeaningfulText(text);

        return new PdfTextMetrics(textDensity, textQuality, hasMeaningfulText);
    }

    private double calculateTextDensity(String text, int pageCount) {
        if (pageCount == 0) return 0;

        // Calculate characters per page
        double charsPerPage = (double) text.length() / pageCount;

        // Normalize against expected minimum chars per page
        return Math.min(charsPerPage / expectedMinCharsPerPage, 1.0);
    }

    private double assessTextQuality(String text) {
        if (text == null || text.isEmpty()) return 0;

        // Count recognizable characters vs total
        int totalChars = text.length();
        int recognizableChars = text.replaceAll("[^a-zA-Z0-9\\s.,;:!?()\\[\\]{}\"'`-]", "").length();

        return (double) recognizableChars / totalChars;
    }

    private boolean detectMeaningfulText(String text) {
        // Check if text contains meaningful sequences of words
        return MEANINGFUL_TEXT_PATTERN.matcher(text).find();
    }

    private boolean shouldUseOcr(PdfTextMetrics metrics, String text) {
        // First check if content have enough text at all
        if (text == null || text.trim().length() < minimumTextLength) {
            return true;
        }

        // Check meaningful text detection
        return !metrics.isHasMeaningfulText() ||
               !(metrics.getTextDensity() >= minTextDensity) ||
               !(metrics.getTextQuality() >= qualityThreshold);
    }
}