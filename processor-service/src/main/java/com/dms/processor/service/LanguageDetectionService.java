package com.dms.processor.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.springframework.stereotype.Service;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LanguageDetectionService {
    private LanguageDetector languageDetector;

    @PostConstruct
    public void init() {
        try {
            this.languageDetector = new OptimaizeLangDetector().loadModels();
            log.info("Language detector initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize language detector", e);
            throw new RuntimeException("Failed to initialize language detector", e);
        }
    }

    public Optional<String> detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Empty text provided for language detection");
            return Optional.empty();
        }

        try {
            // Taking a sample of the text if it's too long (optimize performance)
            String sampleText = text.length() > 1000 ? text.substring(0, 1000) : text;

            LanguageResult result = languageDetector.detect(sampleText);
            if (result.isUnknown() || result.getRawScore() < 0.3) {
                log.debug("Language detection confidence too low or unknown language for text");
                return Optional.empty();
            }

            log.debug("Detected language: {} with confidence: {}", result.getLanguage(), result.getRawScore());
            return Optional.of(result.getLanguage());
        } catch (Exception e) {
            log.error("Error detecting language", e);
            return Optional.empty();
        }
    }

    public List<LanguageResult> detectLanguages(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }

        try {
            String sampleText = text.length() > 1000 ? text.substring(0, 1000) : text;
            return languageDetector.detectAll(sampleText);
        } catch (Exception e) {
            log.error("Error detecting multiple languages", e);
            return List.of();
        }
    }
}