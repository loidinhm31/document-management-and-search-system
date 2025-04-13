package com.dms.processor.service.extraction;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.sax.ToTextContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Component for extracting text content using Apache Tika.
 * This is extracted from the original ContentExtractorServiceImpl to focus
 * specifically on Tika-based text extraction.
 */
@Component
@Slf4j
public class TikaExtractor {

    private final Parser parser = new AutoDetectParser();

    /**
     * Extracts text content from a file using Apache Tika.
     *
     * @param filePath The path to the file
     * @param metadata Map to store extracted metadata
     * @param skipOcr Whether to skip OCR during extraction
     * @return The extracted text content
     */
    public String extractTextContent(Path filePath, Map<String, String> metadata, boolean skipOcr) {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(filePath))) {
            StringWriter writer = new StringWriter();
            ContentHandler handler = new ToTextContentHandler(writer); // Extract text without format
            Metadata tikaMetadata = new Metadata();
            ParseContext context = new ParseContext();

            // Configure Tesseract OCR settings
            TesseractOCRConfig config = new TesseractOCRConfig();
            config.setSkipOcr(skipOcr);

            // If not skipping OCR, configure OCR parameters
            if (!skipOcr) {
                config.setLanguage("eng+vie");
                config.setEnableImagePreprocessing(true);
            }

            context.set(TesseractOCRConfig.class, config);

            parser.parse(input, handler, tikaMetadata, context);

            // Extract and add metadata to the provided map if needed
            if (metadata != null) {
                extractMetadataFromTika(tikaMetadata, metadata);
            }

            return writer.toString();
        } catch (Exception e) {
            log.error("Error extracting text content using Tika", e);
            return "";
        }
    }

    /**
     * Extracts metadata from Tika and adds it to the target map.
     */
    private void extractMetadataFromTika(Metadata tikaMetadata, Map<String, String> targetMap) {
        for (String name : tikaMetadata.names()) {
            String value = tikaMetadata.get(name);
            if (value != null && !value.isEmpty()) {
                // Store with a cleaner key name
                String cleanName = name.replaceAll("^.*:", "").trim();
                targetMap.put(cleanName, value);
            }
        }
    }
}