package com.dms.processor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for PDF text quality metrics
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TextMetrics {

    /**
     * Measure of text density (characters per page relative to expectation)
     */
    private double textDensity;

    /**
     * Measure of text quality (recognizable characters ratio)
     */
    private double textQuality;

    /**
     * Whether the text contains meaningful word sequences
     */
    private boolean hasMeaningfulText;
}