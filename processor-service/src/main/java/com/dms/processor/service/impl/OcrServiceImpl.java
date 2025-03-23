package com.dms.processor.service.impl;

import com.dms.processor.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

@Service
@Slf4j
public class OcrServiceImpl implements OcrService {

    @Value("${app.ocr.minimum-text-length}")
    private int minimumTextLength;

    @Value("${app.ocr.dpi}")
    private float dpi;

    @Value("${app.ocr.image-type}")
    private String imageType;

    private final Tesseract tesseract;

    @Autowired
    public OcrServiceImpl(Tesseract tesseract) {
        this.tesseract = tesseract;
    }

    @Override
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

                String pageText = performOcrOnImage(image);
                if (pageText != null && !pageText.trim().isEmpty()) {
                    extractedText.append(pageText).append("\n");
                    usedOcr = true;
                }
            }
        }

        log.info("PDF processing completed. Used OCR: {}", usedOcr);
        return extractedText.toString();
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


    @Override
    public boolean isImageBasedPdf(Path pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            String pdfText = textStripper.getText(document);
            return pdfText.trim().length() < minimumTextLength;
        }
    }
}