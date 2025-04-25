package com.dms.processor.service.impl;

import com.dms.processor.config.ThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.ContentHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

@Slf4j
@Service
public class LargeFileProcessor {
    private final ThreadPoolManager threadPoolManager;

    @Value("${app.document.chunk-size-mb:5}")
    private int chunkSizeMB;

    public LargeFileProcessor(ThreadPoolManager threadPoolManager) {
        this.threadPoolManager = threadPoolManager;
    }

    public CompletableFuture<String> processLargeFile(Path filePath) {
        return threadPoolManager.submitDocumentTask(() -> {
            try {
                log.info("Starting large file processing for: {}", filePath.getFileName());
                String result = processFileInChunks(filePath);
                log.info("Completed large file processing for: {}", filePath.getFileName());
                return result;
            } catch (Exception e) {
                log.error("Error processing file: {}", filePath, e);
                throw new CompletionException(e);
            }
        });
    }

    protected String processFileInChunks(Path filePath) throws IOException {
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

    protected String processChunk(byte[] buffer, int bytesRead, Parser parser, ParseContext context)
            throws IOException {
        try {
            ContentHandler handler = new ToTextContentHandler();
            Metadata tikaMetadata = new Metadata();

            parser.parse(
                    new ByteArrayInputStream(buffer, 0, bytesRead),
                    handler,
                    tikaMetadata,
                    context
            );

            return handler.toString();
        } catch (Exception e) {
            log.error("Error processing chunk", e);
            throw new IOException("Failed to process file chunk", e);
        }
    }
}