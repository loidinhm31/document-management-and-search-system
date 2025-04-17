package com.dms.processor.service.impl;

import com.dms.processor.config.ThreadPoolManager;
import com.dms.processor.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class OcrLargeFileProcessor {

    private final OcrService ocrService;
    private final ThreadPoolManager threadPoolManager;
    private final AtomicInteger processedPages = new AtomicInteger(0);

    @Value("${app.ocr.chunk-size:10}")
    private int chunkSize;

    @Value("${app.ocr.temp-dir:/tmp/ocr}")
    private String tempDir;

    @Value("${app.ocr.parallel.timeout-minutes:60}")
    private int timeoutMinutes;

    @Value("${app.ocr.data-path}")
    private String tessdataPath;

    @Value("${app.ocr.dpi:300}")
    private float dpi;

    public OcrLargeFileProcessor(OcrService ocrService, ThreadPoolManager threadPoolManager) {
        this.ocrService = ocrService;
        this.threadPoolManager = threadPoolManager;
    }

    public String processLargeFile(Path pdfPath) throws IOException, TesseractException {
        if (Objects.isNull(pdfPath)) {
            throw new IllegalArgumentException("Large file path cannot be null");
        }

        String fileId = pdfPath.getFileName().toString();
        File tempDirectory = createTempDirectory();
        processedPages.set(0);

        try {
            // Extract all pages as image files first
            List<File> pageImageFiles = extractPagesToImages(pdfPath, tempDirectory);
            int totalPages = pageImageFiles.size();

            log.info("Extracted {} page images for processing", totalPages);

            // Try text extraction first - this is a quick check
            String extractedText = ocrService.extractTextFromPdf(pdfPath);
            if (isTextSufficient(extractedText)) {
                log.info("Using direct text extraction for large file - sufficient text found");
                return extractedText;
            }

            log.info("Starting parallel OCR processing for large file with {} pages in chunks of {}",
                    totalPages, chunkSize);

            return processPageImagesInChunks(pageImageFiles, totalPages);

        } finally {
            cleanupTempDirectory(tempDirectory);
        }
    }

    protected List<File> extractPagesToImages(Path pdfPath, File tempDirectory) throws IOException {
        List<File> pageImages = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();

            // Extract each page as an image
            for (int i = 0; i < totalPages; i++) {
                File imageFile = new File(tempDirectory, "page_" + i + ".png");
                BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                ImageIO.write(image, "PNG", imageFile);
                pageImages.add(imageFile);

                // Free memory
                image.flush();

                // Log progress
                if ((i + 1) % 10 == 0 || i == totalPages - 1) {
                    log.info("Extracted {}/{} pages to images", i + 1, totalPages);
                }
            }
        }

        return pageImages;
    }

    protected String processPageImagesInChunks(List<File> pageImages, int totalPages)
            throws TesseractException {

        List<CompletableFuture<String>> chunkFutures = new ArrayList<>();

        // Process in chunks
        for (int chunkStart = 0; chunkStart < pageImages.size(); chunkStart += chunkSize) {
            int chunkEnd = Math.min(chunkStart + chunkSize, pageImages.size());
            List<File> chunkImages = pageImages.subList(chunkStart, chunkEnd);

            chunkFutures.add(processImageChunk(chunkImages, chunkStart, totalPages));
        }

        // Combine results with timeout
        StringBuilder combinedText = new StringBuilder();
        try {
            // Wait for all futures to complete or timeout
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    chunkFutures.toArray(new CompletableFuture[0]));

            // Apply timeout for the entire operation
            allFutures.get(timeoutMinutes, TimeUnit.MINUTES);

            // Collect results in order
            for (CompletableFuture<String> future : chunkFutures) {
                String chunkText = future.get();
                combinedText.append(chunkText).append("\n");
            }
        } catch (TimeoutException e) {
            log.error("OCR processing timed out after {} minutes", timeoutMinutes);
            throw new TesseractException("OCR processing timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("OCR processing was interrupted", e);
            throw new TesseractException("OCR processing was interrupted", e);
        } catch (ExecutionException e) {
            log.error("Error during OCR processing", e.getCause());
            throw new TesseractException("Error during OCR processing", e.getCause());
        }

        return combinedText.toString();
    }

    protected CompletableFuture<String> processImageChunk(
            List<File> chunkImages,
            int startIndex,
            int totalPages) {

        return threadPoolManager.submitOcrTask(() -> {
            StringBuilder chunkText = new StringBuilder();

            // Create a dedicated Tesseract instance for this chunk
            Tesseract chunkTesseract = createTesseractInstance();

            try {
                for (int i = 0; i < chunkImages.size(); i++) {
                    int pageNum = startIndex + i;
                    File imageFile = chunkImages.get(i);

                    try {
                        // Read image
                        BufferedImage image = ImageIO.read(imageFile);

                        // Process with OCR
                        String pageText = chunkTesseract.doOCR(image);
                        if (pageText != null && !pageText.trim().isEmpty()) {
                            chunkText.append(pageText).append("\n");
                        }

                        // Free memory
                        image.flush();

                        // Update progress
                        int completed = processedPages.incrementAndGet();
                        if (completed % 5 == 0 || completed == totalPages) {
                            log.info("Processed page {} of {} ({}%)",
                                    completed, totalPages,
                                    (completed * 100) / totalPages);
                        }

                    } catch (Exception e) {
                        log.error("Error processing page {}", pageNum, e);
                    }
                }

                return chunkText.toString();

            } catch (Exception e) {
                log.error("Error processing chunk starting at page {}", startIndex, e);
                throw new CompletionException(e);
            }
        });
    }

    protected File createTempDirectory() throws IOException {
        File tempDirectory = new File(tempDir, UUID.randomUUID().toString());
        if (!tempDirectory.exists() && !tempDirectory.mkdirs()) {
            throw new IOException("Failed to create temp directory: " + tempDirectory);
        }
        return tempDirectory;
    }

    protected void cleanupTempDirectory(File directory) {
        if (directory != null && directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete()) {
                        log.warn("Failed to delete temporary file: {}", file.getAbsolutePath());
                    }
                }
            }
            if (!directory.delete()) {
                log.warn("Failed to delete temporary directory: {}", directory.getAbsolutePath());
            }
        }
    }

    protected boolean isTextSufficient(String text) {
        if (text == null || text.isEmpty()) return false;

        int minChars = 100;
        int recognizableChars = text.replaceAll("[^a-zA-Z0-9\\s.,;:!?()\\[\\]{}\"'`-]", "").length();

        return recognizableChars > minChars;
    }

    /**
     * Creates a new Tesseract instance with the same configuration as the main instance.
     * This is needed for thread safety when processing in parallel.
     */
    protected Tesseract createTesseractInstance() {
        Tesseract newInstance = new Tesseract();
        newInstance.setDatapath(tessdataPath);
        newInstance.setLanguage("eng+vie");
        newInstance.setPageSegMode(1);
        newInstance.setOcrEngineMode(1);
        newInstance.setVariable("textord_max_iterations", "5");
        return newInstance;
    }
}