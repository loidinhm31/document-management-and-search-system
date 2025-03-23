package com.dms.processor.service.impl;

import com.dms.processor.service.OcrService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class OcrServiceImpl implements OcrService {
    @Value("${app.ocr.dpi:300}")
    private float dpi;

    @Value("${app.ocr.image-type:RGB}")
    private String imageType;

    private final Tesseract tesseract;

    @Autowired
    public OcrServiceImpl(Tesseract tesseract) {
        this.tesseract = tesseract;
    }

    @Override
    public String extractTextFromPdf(Path pdfPath) throws IOException, TesseractException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            String extractedText = processWithOcr(pdfPath, document.getNumberOfPages());
            log.info("PDF processing completed. Used OCR: true");
            return extractedText;
        }
    }

    @Override
    public String processWithOcr(Path pdfPath, int pageCount)
            throws IOException, TesseractException {

        log.debug("Performing OCR on PDF with {} pages", pageCount);

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            return processOcrSequentially(pdfRenderer, pageCount);
        }
    }

    private String processOcrSequentially(PDFRenderer pdfRenderer, int pageCount)
            throws IOException, TesseractException {

        StringBuilder extractedText = new StringBuilder();

        for (int page = 0; page < pageCount; page++) {
            log.debug("Processing page {} of {}", page + 1, pageCount);
            BufferedImage image = renderPage(pdfRenderer, page);
            String pageText = performOcrOnImage(image);

            if (pageText != null && !pageText.trim().isEmpty()) {
                extractedText.append(pageText).append("\n");
            }
        }

        return extractedText.toString();
    }

    private BufferedImage renderPage(PDFRenderer pdfRenderer, int page) throws IOException {
        return pdfRenderer.renderImageWithDPI(
                page,
                dpi,
                imageType.equals("RGB") ? ImageType.RGB : ImageType.BINARY
        );
    }

    @Override
    public String performOcrOnImage(BufferedImage image) throws TesseractException {
        try {
            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            log.error("OCR processing error", e);
            if (e.getMessage().contains("osd.traineddata")) {
                tesseract.setPageSegMode(3); // Fully automatic page segmentation, but no OSD
                return tesseract.doOCR(image);
            }
            throw e;
        }
    }
}