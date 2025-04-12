package com.dms.processor.service.impl;

import com.dms.processor.service.OcrService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class OcrServiceImpl implements OcrService {
    @Value("${app.ocr.dpi:300}")
    private float dpi;

    @Value("${app.ocr.image-type:RGB}")
    private String imageType;

    @Value("${app.ocr.parallel.max-threads:0}")
    private int maxThreads;

    @Value("${app.ocr.data-path}")
    private String tessdataPath;

    @Value("${app.pdf.ocr-page-threshold:5}")
    private int ocrPageThreshold;

    private final Tesseract tesseract;
    private ExecutorService executorService;

    @Autowired
    public OcrServiceImpl(Tesseract tesseract) {
        this.tesseract = tesseract;
    }

    @PostConstruct
    protected void initialize() {
        // If maxThreads is 0 or negative, use available processors
        int threads = maxThreads <= 0 ?
                Runtime.getRuntime().availableProcessors() :
                maxThreads;

        // Create a named thread factory for better debugging
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "ocr-worker-" + threadNumber.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };

        this.executorService = Executors.newFixedThreadPool(threads, threadFactory);
        log.info("Initialized OCR thread pool with {} threads", threads);
    }

    @PreDestroy
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            log.info("OCR thread pool shut down");
        }
    }

    @Override
    public String extractTextFromPdf(Path pdfPath) throws IOException, TesseractException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            int pageCount = document.getNumberOfPages();

            // For very small documents, sequential processing might be more efficient
            if (pageCount <= 1) {
                return processWithOcr(pdfPath, pageCount);
            }

            // For larger documents, use parallel processing
            String extractedText = processWithParallelOcr(pdfPath, pageCount);
            log.info("PDF processing completed with parallel OCR. Pages: {}", pageCount);
            return extractedText;
        }
    }

    @Override
    public String processWithOcr(Path pdfPath, int pageCount)
            throws IOException, TesseractException {
        log.debug("Performing OCR on PDF with {} pages", pageCount);

        // Use parallel OCR for documents with multiple pages
        String ocrText;
        if (pageCount > ocrPageThreshold) {
            log.info("Using parallel OCR for PDF with {} pages", pageCount);
            ocrText = processWithParallelOcr(pdfPath, pageCount);
        } else {
            // Use regular OCR for small documents
            try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                ocrText = processOcrSequentially(pdfRenderer, pageCount);
            }
        }
        return ocrText;
    }

    /**
     * Process a PDF document using parallel OCR.
     *
     * @param pdfPath Path to the PDF file
     * @param pageCount Number of pages in the PDF
     * @return Extracted text from all pages
     * @throws IOException If an error occurs during PDF processing
     * @throws TesseractException If an error occurs during OCR
     */
    protected String processWithParallelOcr(Path pdfPath, int pageCount)
            throws IOException, TesseractException {
        log.debug("Performing parallel OCR on PDF with {} pages", pageCount);

        // We need to save PDF pages as images to process them in parallel
        // to avoid PDFBox thread safety issues
        List<Path> pageImages = new ArrayList<>();
        Path tempDir = Files.createTempDirectory("ocr_parallel_");

        try {
            // Extract pages as images first
            try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
                PDFRenderer pdfRenderer = new PDFRenderer(document);

                // Save each page as an image
                for (int i = 0; i < pageCount; i++) {
                    BufferedImage image = pdfRenderer.renderImageWithDPI(
                            i,
                            dpi,
                            imageType.equals("RGB") ? ImageType.RGB : ImageType.BINARY
                    );

                    // Save to temp file
                    Path imagePath = tempDir.resolve("page_" + i + ".png");
                    javax.imageio.ImageIO.write(image, "PNG", imagePath.toFile());
                    pageImages.add(imagePath);

                    // Release memory
                    image.flush();
                }
            }

            return processImagesInParallel(pageImages);
        } finally {
            // Clean up temp files
            for (Path image : pageImages) {
                try {
                    Files.deleteIfExists(image);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary image: {}", image, e);
                }
            }

            try {
                Files.deleteIfExists(tempDir);
            } catch (IOException e) {
                log.warn("Failed to delete temporary directory: {}", tempDir, e);
            }
        }
    }

    private String processImagesInParallel(List<Path> pageImages) {
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // Process images in parallel
        for (int i = 0; i < pageImages.size(); i++) {
            final int pageNum = i;
            final Path imagePath = pageImages.get(i);

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    log.debug("Processing page {} in parallel", pageNum + 1);

                    // Create a new Tesseract instance for each thread to avoid concurrency issues
                    Tesseract threadTesseract = createTesseractInstance();

                    // Load image and perform OCR
                    BufferedImage image = javax.imageio.ImageIO.read(imagePath.toFile());
                    String text = threadTesseract.doOCR(image);

                    // Release memory
                    image.flush();

                    return text;
                } catch (Exception e) {
                    log.error("Error processing page {} in parallel", pageNum + 1, e);
                    return ""; // Return empty string on error
                }
            }, executorService);

            futures.add(future);
        }

        // Wait for all pages to be processed and combine results
        StringBuilder combinedText = new StringBuilder();

        // Wait for all futures to complete and collect results in order
        for (int i = 0; i < futures.size(); i++) {
            try {
                String pageText = futures.get(i).join();
                if (pageText != null && !pageText.trim().isEmpty()) {
                    combinedText.append(pageText).append("\n");
                }
            } catch (Exception e) {
                log.error("Error retrieving OCR result for page {}", i + 1, e);
            }
        }

        return combinedText.toString();
    }

    protected String processOcrSequentially(PDFRenderer pdfRenderer, int pageCount)
            throws IOException, TesseractException {

        StringBuilder extractedText = new StringBuilder();

        for (int page = 0; page < pageCount; page++) {
            log.debug("Processing page {} of {}", page + 1, pageCount);
            BufferedImage image = renderPage(pdfRenderer, page);
            String pageText = performOcrOnImage(image);

            if (pageText != null && !pageText.trim().isEmpty()) {
                extractedText.append(pageText).append("\n");
            }

            // Explicitly free the image memory
            image.flush();
        }

        return extractedText.toString();
    }

    protected BufferedImage renderPage(PDFRenderer pdfRenderer, int page) throws IOException {
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