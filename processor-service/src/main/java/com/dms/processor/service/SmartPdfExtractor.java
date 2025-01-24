package com.dms.processor.service;

import com.dms.processor.dto.ExtractedText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;


@Service
@RequiredArgsConstructor
@Slf4j
public class SmartPdfExtractor {

    private final OcrService ocrService;

    @Value("${app.pdf.quality-threshold:0.8}")
    private double qualityThreshold;

    @Value("${app.pdf.min-text-density:0.01}")
    private double minTextDensity;

    public ExtractedText extractText(Path pdfPath) throws IOException, TesseractException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            String pdfText = textStripper.getText(document);

            // Calculate text quality metrics
            double textDensity = calculateTextDensity(pdfText, document.getNumberOfPages());
            double textQuality = assessTextQuality(pdfText);

            boolean useOcr = shouldUseOcr(textDensity, textQuality);

            if (!useOcr) {
                log.info("Using PDFTextStripper - Density: {}, Quality: {}", textDensity, textQuality);
                return new ExtractedText(pdfText, false);
            }

            // Use OCR if needed
            log.info("Using OCR - Low text metrics - Density: {}, Quality: {}", textDensity, textQuality);
            String ocrText = ocrService.extractTextFromPdf(pdfPath);
            return new ExtractedText(ocrText, true);
        }
    }

    private double calculateTextDensity(String text, int pageCount) {
        if (pageCount == 0) return 0;

        // Calculate characters per page
        double charsPerPage = (double) text.length() / pageCount;

        // Normalize against expected minimum chars per page
        final double expectedMinCharsPerPage = 250; // Configurable
        return Math.min(charsPerPage / expectedMinCharsPerPage, 1.0);
    }

    private double assessTextQuality(String text) {
        if (text == null || text.isEmpty()) return 0;

        // Count recognizable characters vs total
        int totalChars = text.length();
        int recognizableChars = text.replaceAll("[^a-zA-Z0-9\\s.,;:!?()\\[\\]{}\"'`-]", "").length();

        return (double) recognizableChars / totalChars;
    }

    private boolean shouldUseOcr(double textDensity, double textQuality) {
        return textDensity < minTextDensity || textQuality < qualityThreshold;
    }
}