package com.dms.processor.config;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TesseractConfig {
    @Value("${app.ocr.data-path}")
    private String tessdataPath;

    @Bean
    public Tesseract tesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage("eng+vie");

        // Configure Tesseract for better accuracy
        tesseract.setPageSegMode(1);  // Automatic page segmentation with OSD
        tesseract.setOcrEngineMode(1); // Neural net LSTM engine only

        // Additional settings for better performance
        tesseract.setVariable("textord_max_iterations", "5");

        return tesseract;
    }
}
