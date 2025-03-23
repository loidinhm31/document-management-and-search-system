package com.dms.processor.service;

import org.apache.tika.language.detect.LanguageResult;
import java.util.List;
import java.util.Optional;

/**
 * Interface for language detection operations
 */
public interface LanguageDetectionService {

    /**
     * Detects the primary language of the provided text
     *
     * @param text The text to analyze for language detection
     * @return Optional containing the detected language code, or empty if detection failed or confidence is too low
     */
    Optional<String> detectLanguage(String text);

    /**
     * Detects all possible languages in the provided text
     *
     * @param text The text to analyze for multiple language detection
     * @return List of language results with their confidence scores
     */
    List<LanguageResult> detectLanguages(String text);
}