package com.dms.processor.service;

import com.dms.processor.dto.TextMetrics;

/**
 * Interface for analyzing text quality and determining if OCR should be used.
 * This allows reusing the same text quality assessment logic across different file types.
 */
public interface TextQualityAnalyzer {

    /**
     * Analyzes the quality of text content
     *
     * @param text The text content to analyze
     * @param estimatedPages Estimated number of pages in the document
     * @return Metrics about the text quality
     */
    TextMetrics analyzeTextQuality(String text, int estimatedPages);

    /**
     * Determines if OCR should be used based on text metrics
     *
     * @param metrics The text quality metrics
     * @param text The original text content
     * @return true if OCR should be used, false otherwise
     */
    boolean shouldUseOcr(TextMetrics metrics, String text);
}