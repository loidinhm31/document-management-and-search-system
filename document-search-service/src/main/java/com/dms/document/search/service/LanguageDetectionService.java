package com.dms.document.search.service;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Language detection service using Lingua library for search query analysis
 */
@Service
@Slf4j
public class LanguageDetectionService {

    private final LanguageDetector detector;

    // Minimum text length for reliable detection
    private static final int MIN_TEXT_LENGTH = 3;

    // Confidence threshold for language detection
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    public LanguageDetectionService() {
        // Initialize detector with languages we support
        this.detector = LanguageDetectorBuilder.fromLanguages(
                Language.ENGLISH,
                Language.KOREAN,
                Language.VIETNAMESE
        ).build();

        log.info("Language detection service initialized with support for: English, Korean, Vietnamese");
    }

    /**
     * Detect language of search query text
     *
     * @param text the search query text
     * @return detected language code ("en", "ko", "vi", or fallback)
     */
    public String detectLanguage(String text) {
        if (text == null || text.trim().length() < MIN_TEXT_LENGTH) {
            return "en"; // Default to English for very short text
        }

        try {
            Language detectedLanguage = detector.detectLanguageOf(text);

            double confidence = detector.computeLanguageConfidenceValues(text).size();

            if (confidence >= CONFIDENCE_THRESHOLD) {
                String languageCode = detectedLanguage.getIsoCode639_1().toString().toLowerCase();
                log.debug("Detected language: {} with confidence: {}", languageCode, confidence);
                return languageCode;
            }
        } catch (Exception e) {
            log.warn("Language detection failed for text: '{}', using English fallback",
                    text.length() > 50 ? text.substring(0, 50) + "..." : text, e);
        }

        return "en"; // Fallback to English
    }

    /**
     * Check if text is Korean
     */
    public boolean isKorean(String text) {
        return "ko".equals(detectLanguage(text));
    }

    /**
     * Check if text is Vietnamese
     */
    public boolean isVietnamese(String text) {
        return "vi".equals(detectLanguage(text));
    }

    /**
     * Check if text is English (or fallback)
     */
    public boolean isEnglish(String text) {
        return "en".equals(detectLanguage(text));
    }

    /**
     * Get language with confidence score for debugging/monitoring
     */
    public LanguageResult detectLanguageWithConfidence(String text) {
        if (text == null || text.trim().length() < MIN_TEXT_LENGTH) {
            return new LanguageResult("en", 0.0, "Text too short");
        }

        try {
            Language detectedLanguage = detector.detectLanguageOf(text);

            double confidence = detector.computeLanguageConfidenceValues(text).size();
            String languageCode = detectedLanguage.getIsoCode639_1().toString().toLowerCase();

            if (confidence >= CONFIDENCE_THRESHOLD) {
                return new LanguageResult(languageCode, confidence, "Detected");
            } else {
                return new LanguageResult("en", confidence, "Low confidence, fallback to English");
            }
        } catch (Exception e) {
            log.warn("Language detection failed", e);
            return new LanguageResult("en", 0.0, "Detection failed: " + e.getMessage());
        }
    }

    /**
     * Result object for language detection with metadata
     */
    public static class LanguageResult {
        private final String languageCode;
        private final double confidence;
        private final String reason;

        public LanguageResult(String languageCode, double confidence, String reason) {
            this.languageCode = languageCode;
            this.confidence = confidence;
            this.reason = reason;
        }

        public String getLanguageCode() { return languageCode; }
        public double getConfidence() { return confidence; }
        public String getReason() { return reason; }
        public boolean isReliable() { return confidence >= CONFIDENCE_THRESHOLD; }

        @Override
        public String toString() {
            return String.format("LanguageResult{language='%s', confidence=%.2f, reason='%s'}",
                    languageCode, confidence, reason);
        }
    }
}