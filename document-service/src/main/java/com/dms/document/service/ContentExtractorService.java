package com.dms.document.service;

import com.dms.document.dto.DocumentContent;
import com.dms.document.dto.ExtractedText;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ContentExtractorService {
    private final AutoDetectParser parser;
    private final ExecutorService executorService;
    private final SmartPdfExtractor smartPdfExtractor;
    private final LargeFileProcessor largeFileProcessor;

    private final AtomicInteger threadCounter = new AtomicInteger(1);

    @Value("${app.document.max-size-threshold-mb}")
    private DataSize maxSizeThreshold;

    public ContentExtractorService(SmartPdfExtractor smartPdfExtractor, LargeFileProcessor largeFileProcessor) {
        this.smartPdfExtractor = smartPdfExtractor;
        this.largeFileProcessor = largeFileProcessor;
        this.parser = new AutoDetectParser();
        this.executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("content-extractor-" + threadCounter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    public DocumentContent extractContent(Path filePath) {
        try {
            String mimeType = Files.probeContentType(filePath);
            if (mimeType == null) {
                log.warn("Could not determine mime type for file: {}", filePath);
                return new DocumentContent("", new HashMap<>());
            }

            // Initialize metadata map
            Map<String, String> metadata = new HashMap<>();
            metadata.put("Content-Type", mimeType);
            metadata.put("File-Size", String.valueOf(Files.size(filePath)));

            String extractedText;
            if ("application/pdf".equals(mimeType)) {
                extractedText = handlePdfExtraction(filePath, metadata);
            } else {
                // Handle other file types with existing logic
                extractedText = extractTextContent(filePath);
                metadata.putAll(extractMetadata(filePath));
            }

            return new DocumentContent(extractedText, metadata);
        } catch (Exception e) {
            log.error("Error extracting content from file: {}", filePath, e);
            return new DocumentContent("", new HashMap<>());
        }
    }


    private String handlePdfExtraction(Path filePath, Map<String, String> metadata) throws IOException, TesseractException {
        long fileSizeInMb = Files.size(filePath) / (1024 * 1024);
        metadata.put("File-Size-MB", String.valueOf(fileSizeInMb));

        if (fileSizeInMb > maxSizeThreshold.toBytes()) {
            log.info("Large PDF detected ({}MB). Using chunked processing.", fileSizeInMb);
            metadata.put("Processing-Method", "chunked");
            return largeFileProcessor.processLargePdf(filePath);
        }

        log.info("Processing regular PDF ({}MB)", fileSizeInMb);
        ExtractedText result = smartPdfExtractor.extractText(filePath);
        metadata.put("Processing-Method", result.usedOcr() ? "ocr" : "direct");
        metadata.put("Used-OCR", String.valueOf(result.usedOcr()));

        return result.text();
    }

    // Existing methods for other file types
    private String extractTextContent(Path filePath) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(filePath))) {
            StringWriter writer = new StringWriter();
            ContentHandler handler = new ToTextContentHandler(writer);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            parser.parse(input, handler, metadata, context);
            return writer.toString();

        } catch (Exception e) {
            log.error("Error extracting text content", e);
            return "";
        }
    }

    private Map<String, String> extractMetadata(Path filePath) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(filePath))) {
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            ContentHandler handler = new DefaultHandler();

            parser.parse(input, handler, metadata, context);

            return Arrays.stream(metadata.names())
                    .filter(this::isImportantMetadata)
                    .collect(Collectors.toMap(
                            name -> name,
                            metadata::get,
                            (v1, v2) -> v1
                    ));

        } catch (Exception e) {
            log.error("Error extracting metadata", e);
            return new HashMap<>();
        }
    }

    private boolean isImportantMetadata(String name) {
        return Set.of(
                "Content-Type",
                "Last-Modified",
                "Creation-Date",
                "Author",
                "Page-Count",
                "Word-Count"
        ).contains(name);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}