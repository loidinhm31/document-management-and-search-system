package com.dms.document.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class OcrLargeFileProcessor {

    private final OcrService ocrService;
    private ExecutorService executorService;

    @Value("${app.ocr.chunk-size:10}")
    private int chunkSize;

    @Value("${app.ocr.temp-dir:/tmp/ocr}")
    private String tempDir;

    @Value("${app.ocr.max-threads:4}")
    private int maxThreads;

    @PostConstruct
    private void initialize() {
        this.executorService = Executors.newFixedThreadPool(maxThreads);
        log.info("Initialized thread pool with {} threads", maxThreads);
    }

    public String processLargePdf(Path pdfPath) throws IOException, TesseractException {
        File tempDirectory = createTempDirectory();
        AtomicInteger processedPages = new AtomicInteger(0);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            int totalPages = document.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(document);

            // Try text extraction first
            String extractedText = ocrService.extractTextFromPdf(pdfPath);
            if (isTextSufficient(extractedText)) {
                return extractedText;
            }

            // Process in chunks
            for (int chunkStart = 0; chunkStart < totalPages; chunkStart += chunkSize) {
                int chunkEnd = Math.min(chunkStart + chunkSize, totalPages);
                futures.add(processChunk(renderer, chunkStart, chunkEnd, tempDirectory, processedPages, totalPages));
            }

            // Combine results
            StringBuilder combinedText = new StringBuilder();
            for (CompletableFuture<String> future : futures) {
                try {
                    String chunkText = future.get();
                    combinedText.append(chunkText).append("\n");
                } catch (Exception e) {
                    log.error("Error processing PDF chunk", e);
                }
            }

            return combinedText.toString();
        } finally {
            cleanupTempDirectory(tempDirectory);
        }
    }

    private CompletableFuture<String> processChunk(
            PDFRenderer renderer,
            int startPage,
            int endPage,
            File tempDirectory,
            AtomicInteger processedPages,
            int totalPages) {

        return CompletableFuture.supplyAsync(() -> {
            StringBuilder chunkText = new StringBuilder();

            try {
                for (int pageNum = startPage; pageNum < endPage; pageNum++) {
                    BufferedImage image = renderer.renderImageWithDPI(pageNum, 300);

                    // Preprocess the image
                    image = ocrService.preprocessImage(image);

                    // Save image temporarily
                    File tempImage = new File(tempDirectory, UUID.randomUUID() + ".png");
                    ImageIO.write(image, "PNG", tempImage);

                    // Process with OCR using the public method
                    String pageText = ocrService.performOcrOnImage(image);
                    chunkText.append(pageText).append("\n");

                    tempImage.delete();

                    int completed = processedPages.incrementAndGet();
                    log.info("Processed page {} of {} ({}%)",
                            completed, totalPages,
                            (completed * 100) / totalPages);

                    image.flush();
                }

                return chunkText.toString();

            } catch (Exception e) {
                log.error("Error processing pages {} to {}", startPage, endPage, e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    private File createTempDirectory() throws IOException {
        File tempDirectory = new File(tempDir, UUID.randomUUID().toString());
        if (!tempDirectory.exists() && !tempDirectory.mkdirs()) {
            throw new IOException("Failed to create temp directory: " + tempDirectory);
        }
        return tempDirectory;
    }

    private void cleanupTempDirectory(File directory) {
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

    private boolean isTextSufficient(String text) {
        if (text == null || text.isEmpty()) return false;

        int minChars = 100;
        int recognizableChars = text.replaceAll("[^a-zA-Z0-9\\s.,;:!?()\\[\\]{}\"'`-]", "").length();

        return recognizableChars > minChars;
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}