package com.dms.processor.service.extraction;

import com.dms.processor.dto.ExtractedText;
import com.dms.processor.dto.TextMetrics;
import com.dms.processor.service.OcrService;
import com.dms.processor.service.TextQualityAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Component for analyzing the content quality of documents and extracting text.
 * This is extracted from the original ContentQualityAnalyzer to focus specifically
 * on analyzing PDF content quality and making extraction decisions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentQualityAnalysis {

    private final OcrService ocrService;
    private final TextQualityAnalyzer textQualityAnalyzer;

    /**
     * Extracts text from a PDF file, using OCR only when necessary.
     *
     * @param pdfPath Path to the PDF file
     * @return Extracted text and information about whether OCR was used
     */
    public ExtractedText extractText(Path pdfPath) throws IOException, TesseractException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            int pageCount = document.getNumberOfPages();

            // Extract text and calculate metrics
            PDFTextStripper textStripper = new PDFTextStripper();
            String pdfText = textStripper.getText(document);

            TextMetrics metrics = calculateTextMetrics(pdfText, pageCount);
            log.debug("PDF metrics - Density: {}, Quality: {}, HasMeaningfulText: {}",
                    metrics.getTextDensity(), metrics.getTextQuality(), metrics.isHasMeaningfulText());

            // Decide whether to use OCR based on metrics and text length
            if (!textQualityAnalyzer.shouldUseOcr(metrics, pdfText)) {
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

    /**
     * Calculate text metrics from extracted text and page count.
     */
    private TextMetrics calculateTextMetrics(String text, int pageCount) {
        return textQualityAnalyzer.analyzeTextQuality(text, pageCount);
    }
}