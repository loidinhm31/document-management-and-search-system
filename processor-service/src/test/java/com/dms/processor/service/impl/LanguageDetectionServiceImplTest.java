package com.dms.processor.service.impl;

import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LanguageDetectionServiceImplTest {

    @Mock
    private OptimaizeLangDetector mockDetector;

    @Spy
    @InjectMocks
    private LanguageDetectionServiceImpl languageDetectionService;

    @BeforeEach
    void setUp() throws Exception {
        // Set the private languageDetector field
        java.lang.reflect.Field field = LanguageDetectionServiceImpl.class.getDeclaredField("languageDetector");
        field.setAccessible(true);
        field.set(languageDetectionService, mockDetector);
    }

    @Test
    void testDetectLanguage_SupportedLanguageWithHighConfidence() {
        // Arrange
        String text = "This is English text with high confidence";
        LanguageResult result = mock(LanguageResult.class);
        when(result.getLanguage()).thenReturn("en");
        when(result.isUnknown()).thenReturn(false);
        when(result.getRawScore()).thenReturn(0.95F);
        when(mockDetector.detect(anyString())).thenReturn(result);

        // Act
        Optional<String> detectedLang = languageDetectionService.detectLanguage(text);

        // Assert
        assertTrue(detectedLang.isPresent());
        assertEquals("en", detectedLang.get());
        verify(mockDetector).detect(text);
    }

    @Test
    void testDetectLanguage_SupportedLanguageVietnamese() {
        // Arrange
        String text = "Đây là một văn bản bằng tiếng Việt";
        LanguageResult result = mock(LanguageResult.class);
        when(result.getLanguage()).thenReturn("vi");
        when(result.isUnknown()).thenReturn(false);
        when(result.getRawScore()).thenReturn(0.9F);
        when(mockDetector.detect(anyString())).thenReturn(result);

        // Act
        Optional<String> detectedLang = languageDetectionService.detectLanguage(text);

        // Assert
        assertTrue(detectedLang.isPresent());
        assertEquals("vi", detectedLang.get());
    }

    @Test
    void testDetectLanguage_UnsupportedLanguage() {
        // Arrange
        String text = "Este es un texto en español";
        LanguageResult result = mock(LanguageResult.class);
        when(result.getLanguage()).thenReturn("es");
        when(result.isUnknown()).thenReturn(false);
        when(result.getRawScore()).thenReturn(0.9F);
        when(mockDetector.detect(anyString())).thenReturn(result);

        // Act
        Optional<String> detectedLang = languageDetectionService.detectLanguage(text);

        // Assert
        assertFalse(detectedLang.isPresent());
    }

    @Test
    void testDetectLanguage_LowConfidence() {
        // Arrange
        String text = "This text has mixed languages English and 这是中文";
        LanguageResult result = mock(LanguageResult.class);
        lenient().when(result.getLanguage()).thenReturn("en");
        when(result.isUnknown()).thenReturn(false);
        when(result.getRawScore()).thenReturn(0.2F);
        when(mockDetector.detect(anyString())).thenReturn(result);

        // Act
        Optional<String> detectedLang = languageDetectionService.detectLanguage(text);

        // Assert
        assertFalse(detectedLang.isPresent());
    }

    @Test
    void testDetectLanguage_NullText() {
        // Act
        Optional<String> detectedLang = languageDetectionService.detectLanguage(null);

        // Assert
        assertFalse(detectedLang.isPresent());
        verify(mockDetector, never()).detect(anyString());
    }

    @Test
    void testDetectLanguage_EmptyText() {
        // Act
        Optional<String> detectedLang = languageDetectionService.detectLanguage("");

        // Assert
        assertFalse(detectedLang.isPresent());
        verify(mockDetector, never()).detect(anyString());
    }

    @Test
    void testDetectLanguage_WhitespaceText() {
        // Act
        Optional<String> detectedLang = languageDetectionService.detectLanguage("   ");

        // Assert
        assertFalse(detectedLang.isPresent());
        verify(mockDetector, never()).detect(anyString());
    }

    @Test
    void testDetectLanguage_ExceptionInDetection() {
        // Arrange
        String text = "Sample text";
        when(mockDetector.detect(anyString())).thenThrow(new RuntimeException("Test exception"));

        // Act
        Optional<String> detectedLang = languageDetectionService.detectLanguage(text);

        // Assert
        assertFalse(detectedLang.isPresent());
    }

    @Test
    void testDetectLanguage_UnknownLanguage() {
        // Arrange
        String text = "Sample text";
        LanguageResult result = mock(LanguageResult.class);
        when(result.isUnknown()).thenReturn(true);
        when(mockDetector.detect(anyString())).thenReturn(result);

        // Act
        Optional<String> detectedLang = languageDetectionService.detectLanguage(text);

        // Assert
        assertFalse(detectedLang.isPresent());
    }

    @Test
    void testDetectLanguage_TextSampling() {
        // Arrange
        String text = "Large text ".repeat(2000);

        LanguageResult result = mock(LanguageResult.class);
        when(result.getLanguage()).thenReturn("en");
        when(result.isUnknown()).thenReturn(false);
        when(result.getRawScore()).thenReturn(0.9F);
        when(mockDetector.detect(anyString())).thenReturn(result);

        // Act
        languageDetectionService.detectLanguage(text);

        // Assert
        // Verify that only the first 1000 characters were used
        verify(mockDetector).detect(text.substring(0, 1000));
    }

    @Test
    void testDetectLanguages_ValidText() {
        // Arrange
        String text = "Multilingual text";

        LanguageResult result1 = mock(LanguageResult.class);
        lenient().when(result1.getLanguage()).thenReturn("en");

        LanguageResult result2 = mock(LanguageResult.class);
        lenient().when(result2.getLanguage()).thenReturn("vi");

        LanguageResult result3 = mock(LanguageResult.class);
        lenient().when(result3.getLanguage()).thenReturn("fr");

        List<LanguageResult> results = Arrays.asList(result1, result2, result3);
        when(mockDetector.detectAll(anyString())).thenReturn(results);

        // Act
        List<LanguageResult> detectedLangs = languageDetectionService.detectLanguages(text);

        // Assert
        assertEquals(3, detectedLangs.size());
        assertEquals(results, detectedLangs);
    }

    @Test
    void testDetectLanguages_NullText() {
        // Act
        List<LanguageResult> detectedLangs = languageDetectionService.detectLanguages(null);

        // Assert
        assertTrue(detectedLangs.isEmpty());
        verify(mockDetector, never()).detectAll(anyString());
    }

    @Test
    void testDetectLanguages_EmptyText() {
        // Act
        List<LanguageResult> detectedLangs = languageDetectionService.detectLanguages("");

        // Assert
        assertTrue(detectedLangs.isEmpty());
        verify(mockDetector, never()).detectAll(anyString());
    }

    @Test
    void testDetectLanguages_ExceptionInDetection() {
        // Arrange
        String text = "Sample text";
        when(mockDetector.detectAll(anyString())).thenThrow(new RuntimeException("Test exception"));

        // Act
        List<LanguageResult> detectedLangs = languageDetectionService.detectLanguages(text);

        // Assert
        assertTrue(detectedLangs.isEmpty());
    }

    @Test
    void testDetectLanguages_TextSampling() {
        // Arrange
        String text = "Large text ".repeat(2000);

        List<LanguageResult> results = Arrays.asList(
                mock(LanguageResult.class),
                mock(LanguageResult.class)
        );
        when(mockDetector.detectAll(anyString())).thenReturn(results);

        // Act
        languageDetectionService.detectLanguages(text);

        // Assert
        // Verify that only the first 1000 characters were used
        verify(mockDetector).detectAll(text.substring(0, 1000));
    }

    @Test
    void testSupportedLanguages() throws Exception {
        // Access the private SUPPORT_LANGUAGES field to verify content
        java.lang.reflect.Field field = LanguageDetectionServiceImpl.class.getDeclaredField("SUPPORT_LANGUAGES");
        field.setAccessible(true);
        Set<String> supportedLanguages = (Set<String>) field.get(languageDetectionService);

        assertTrue(supportedLanguages.contains("en"));
        assertTrue(supportedLanguages.contains("vi"));
        assertEquals(2, supportedLanguages.size());
    }
}