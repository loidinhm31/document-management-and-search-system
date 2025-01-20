package com.dms.document.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class LargeFileProcessor {
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, CompletableFuture<String>> processingTasks;
    private final AtomicInteger threadCounter = new AtomicInteger(1);

    @Value("${app.document.chunk-size-mb:5}")
    private int chunkSizeMB;

    public LargeFileProcessor() {
        this.executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("large-file-handler-" + threadCounter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
        );
        this.processingTasks = new ConcurrentHashMap<>();
    }

    public CompletableFuture<String> processLargeFile(Path filePath) {
        String fileId = filePath.getFileName().toString();

        return processingTasks.computeIfAbsent(fileId, id -> {
            CompletableFuture<String> future = new CompletableFuture<>();

            CompletableFuture.runAsync(() -> {
                try {
                    String result = processFileInChunks(filePath);
                    future.complete(result);
                } catch (Exception e) {
                    log.error("Error processing file: {}", filePath, e);
                    future.completeExceptionally(e);
                } finally {
                    processingTasks.remove(fileId);
                }
            }, executorService);

            return future;
        });
    }

    private String processFileInChunks(Path filePath) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(filePath))) {
            long fileSize = Files.size(filePath);
            int chunkSize = chunkSizeMB * 1024 * 1024; // Convert MB to bytes
            long totalChunks = (fileSize + chunkSize - 1) / chunkSize;

            StringBuilder result = new StringBuilder();
            byte[] buffer = new byte[chunkSize];
            int chunkNumber = 0;
            int bytesRead;

            Parser parser = new AutoDetectParser();
            ParseContext context = new ParseContext();

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                chunkNumber++;
                log.info("Processing chunk {}/{} for file: {}",
                        chunkNumber, totalChunks, filePath.getFileName());

                String chunkText = processChunk(buffer, bytesRead, parser, context);
                result.append(chunkText);

                // Add progress tracking
                double progress = (chunkNumber * 100.0) / totalChunks;
                log.info("Progress: {}% for file: {}",
                        String.format("%.2f", progress), filePath.getFileName());
            }

            return result.toString();
        }
    }

    private String processChunk(byte[] buffer, int bytesRead, Parser parser, ParseContext context)
            throws IOException {
        try {
            ToTextContentHandler handler = new ToTextContentHandler();
            Metadata metadata = new Metadata();

            parser.parse(
                    new ByteArrayInputStream(buffer, 0, bytesRead),
                    handler,
                    metadata,
                    context
            );

            return handler.toString();
        } catch (Exception e) {
            log.error("Error processing chunk", e);
            throw new IOException("Failed to process file chunk", e);
        }
    }

    public void cancelProcessing(String fileId) {
        CompletableFuture<String> task = processingTasks.get(fileId);
        if (task != null) {
            task.cancel(true);
            processingTasks.remove(fileId);
        }
    }

    public double getProcessingProgress(String fileId) {
        // Implementation for progress tracking
        // This could be enhanced with a more sophisticated progress tracking mechanism
        return processingTasks.containsKey(fileId) ? -1 : 100;
    }
}