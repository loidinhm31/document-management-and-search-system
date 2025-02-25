package com.dms.processor.service;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.dto.ExtractedText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.sax.ToTextContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.xml.sax.ContentHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
@RequiredArgsConstructor
public class ContentExtractorService {

    @Value("${app.document.max-size-threshold-mb}")
    private DataSize maxSizeThreshold;

    private final AutoDetectParser parser = new AutoDetectParser();
    private final SmartPdfExtractor smartPdfExtractor;
    private final OcrLargeFileProcessor ocrLargeFileProcessor;

    private final LargeFileProcessor largeFileProcessor;

    public DocumentExtractContent extractContent(Path filePath) {
        try {
            String mimeType = Files.probeContentType(filePath);
            if (mimeType == null) {
                log.warn("Could not determine mime type for file: {}", filePath);
                return new DocumentExtractContent("", new HashMap<>());
            }

            // Initialize metadata map
            Map<String, String> metadata = new HashMap<>();

            String extractedText;
            if ("application/pdf".equals(mimeType)) {
                extractedText = handlePdfExtraction(filePath, metadata);
                return new DocumentExtractContent(extractedText, metadata);
            } else {
                if (Files.size(filePath) > maxSizeThreshold.toBytes()) {
                    // Use new large file handler for non-PDF large files
                    CompletableFuture<String> future = largeFileProcessor.processLargeFile(filePath);
                    String content = future.get(30, TimeUnit.MINUTES);
                    return new DocumentExtractContent(content, metadata);
                } else {
                    // Handle other file types with existing logic
                    String result = tikaExtractTextContent(filePath);

                    // Fallback to basic text extraction if tika cannot handle
                    if (StringUtils.isBlank(result)) {
                        result = basicExtractTextContent(filePath);
                    }

                    return new DocumentExtractContent(result, metadata);
                }

            }
        } catch (Exception e) {
            log.error("Error extracting content from file: {}", filePath, e);
            return new DocumentExtractContent("", new HashMap<>());
        }
    }

    private String basicExtractTextContent(Path filePath) {
        // Use larger buffer size for better performance with large files
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath.toFile()), StandardCharsets.UTF_8),
                8192 * 4)) { // 32KB buffer

            StringBuilder content = new StringBuilder();
            char[] buffer = new char[8192]; // 8KB chunks
            int numChars;

            while ((numChars = reader.read(buffer)) != -1) {
                content.append(buffer, 0, numChars);
            }

            return content.toString();
        } catch (IOException e) {
            log.error("Error reading file content from {}: {}", filePath, e.getMessage());
            return "";
        }
    }

    private String handlePdfExtraction(Path filePath, Map<String, String> metadata) throws IOException, TesseractException {
        long fileSizeInMb = Files.size(filePath) / (1024 * 1024);
        metadata.put("File-Size-MB", String.valueOf(fileSizeInMb));

        if (fileSizeInMb > maxSizeThreshold.toBytes()) {
            log.info("Large PDF detected ({}MB). Using chunked processing.", fileSizeInMb);
            metadata.put("Processing-Method", "chunked");
            return ocrLargeFileProcessor.processLargePdf(filePath);
        }

        log.info("Processing regular PDF ({}MB)", fileSizeInMb);
        ExtractedText result = smartPdfExtractor.extractText(filePath);
        metadata.put("Processing-Method", result.usedOcr() ? "ocr" : "direct");
        metadata.put("Used-OCR", String.valueOf(result.usedOcr()));

        return result.text();
    }

    private String tikaExtractTextContent(Path filePath) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(filePath))) {
            StringWriter writer = new StringWriter();
            ContentHandler handler = new ToTextContentHandler(writer); // Extract text without format
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            // Configure Tesseract to skip OCR for non-PDF files
            TesseractOCRConfig config = new TesseractOCRConfig();
            config.setSkipOcr(true);
            context.set(TesseractOCRConfig.class, config);

            parser.parse(input, handler, metadata, context);
            return writer.toString();
        } catch (Exception e) {
            log.error("Error extracting text content", e);
            return "";
        }
    }

}