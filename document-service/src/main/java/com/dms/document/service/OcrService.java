package com.dms.document.service;

import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

@Service
public class OcrService {
    private static final Logger logger = LoggerFactory.getLogger(OcrService.class);
    private final Tesseract tesseract;

    @Value("${app.ocr.data-path}")
    private String tessdataPath;

    @Value("${app.ocr.minimum-text-length}")
    private int minimumTextLength;

    @Value("${app.ocr.dpi}")
    private float dpi;

    @Value("${app.ocr.image-type}")
    private String imageType;

    public OcrService() {
        tesseract = new Tesseract();
    }

    @PostConstruct
    private void initialize() {
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage("eng+vie");

        // Configure Tesseract for better accuracy
        tesseract.setPageSegMode(1);  // Automatic page segmentation with OSD
        tesseract.setOcrEngineMode(1); // Neural net LSTM engine only

        // Additional settings for better performance
        tesseract.setVariable("textord_max_iterations", "5");
    }

    public String extractTextFromPdf(Path pdfPath) throws IOException, TesseractException {
        StringBuilder extractedText = new StringBuilder();
        boolean usedOcr = false;

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            String pdfText = textStripper.getText(document);

            if (pdfText.trim().length() > minimumTextLength) {
                return pdfText;
            }

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(
                        page,
                        dpi,
                        imageType.equals("RGB") ? ImageType.RGB : ImageType.BINARY
                );

                image = preprocessImage(image);
                String pageText = performOcrOnImage(image);
                if (pageText != null && !pageText.trim().isEmpty()) {
                    extractedText.append(pageText).append("\n");
                    usedOcr = true;
                }
            }
        }

        logger.info("PDF processing completed. Used OCR: {}", usedOcr);
        return extractedText.toString();
    }

    // Made public for use by LargeFileProcessor
    public String performOcrOnImage(BufferedImage image) throws TesseractException {
        try {
            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            logger.error("OCR processing error", e);
            if (e.getMessage().contains("osd.traineddata")) {
                tesseract.setPageSegMode(3); // Fully automatic page segmentation, but no OSD
                return tesseract.doOCR(image);
            }
            throw e;
        }
    }

    // Also made public for potential external preprocessing
    public BufferedImage preprocessImage(BufferedImage image) {
        // Add any image preprocessing here if needed
        return image;
    }

    public boolean isImageBasedPdf(Path pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            String pdfText = textStripper.getText(document);
            return pdfText.trim().length() < minimumTextLength;
        }
    }
}