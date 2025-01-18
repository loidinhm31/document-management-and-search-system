package com.sdms.document.service;

import com.sdms.document.dto.DocumentContent;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContentExtractorService {
    private final AutoDetectParser parser;
    private final ExecutorService executorService;

    private final AtomicInteger threadCounter = new AtomicInteger(1);

    public ContentExtractorService() {
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
            CompletableFuture<String> contentFuture = CompletableFuture.supplyAsync(() ->
                    extractTextContent(filePath), executorService);

            CompletableFuture<Map<String, String>> metadataFuture = CompletableFuture.supplyAsync(() ->
                    extractMetadata(filePath), executorService);

            CompletableFuture.allOf(contentFuture, metadataFuture).join();

            return new DocumentContent(contentFuture.get(), metadataFuture.get());

        } catch (Exception e) {
            log.error("Error extracting content from file: {}", filePath, e);
            return new DocumentContent("", new HashMap<>());
        }
    }

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