package com.dms.processor.service;

import net.sourceforge.tess4j.TesseractException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Service interface for OCR (Optical Character Recognition) operations.
 * Defines methods for extracting text from PDFs and images.
 */
public interface OcrService {

    /**
     * Extracts text from a PDF file. If the PDF contains extractable text,
     * it will be returned directly. Otherwise, OCR will be performed.
     *
     * @param pdfPath Path to the PDF file
     * @return Extracted text from the PDF
     * @throws IOException If there is an error reading the file
     * @throws TesseractException If there is an error during OCR processing
     */
    String extractTextFromPdf(Path pdfPath) throws IOException, TesseractException;

    /**
     * Performs OCR on an image to extract text.
     *
     * @param image BufferedImage to process
     * @return Extracted text from the image
     * @throws TesseractException If there is an error during OCR processing
     */
    String performOcrOnImage(BufferedImage image) throws TesseractException;

    /**
     * Determines if a PDF is image-based (requires OCR) or text-based.
     *
     * @param pdfPath Path to the PDF file
     * @return true if the PDF is image-based, false otherwise
     * @throws IOException If there is an error reading the file
     */
    boolean isImageBasedPdf(Path pdfPath) throws IOException;
}