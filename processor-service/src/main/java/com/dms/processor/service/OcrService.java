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
     * Extracts text from a PDF file using OCR.
     *
     * @param pdfPath Path to the PDF file
     * @return Extracted text from the PDF
     * @throws IOException If there is an error reading the file
     * @throws TesseractException If there is an error during OCR processing
     */
    String extractTextFromPdf(Path pdfPath) throws IOException, TesseractException;

    /**
     * Processes a PDF with OCR. The decision to use OCR has already been made
     * by the caller, so this method will always perform OCR.
     *
     * @param pdfPath Path to the PDF file
     * @param pageCount Number of pages in the document
     * @return Extracted text from the PDF using OCR
     * @throws IOException If there is an error reading the file
     * @throws TesseractException If there is an error during OCR processing
     */
    String processWithOcr(Path pdfPath, int pageCount)
            throws IOException, TesseractException;

    /**
     * Performs OCR on an image to extract text.
     *
     * @param image BufferedImage to process
     * @return Extracted text from the image
     * @throws TesseractException If there is an error during OCR processing
     */
    String performOcrOnImage(BufferedImage image) throws TesseractException;
}