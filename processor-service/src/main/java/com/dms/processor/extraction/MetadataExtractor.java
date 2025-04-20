package com.dms.processor.extraction;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Component for extracting metadata from document files.
 */
@Component
@Slf4j
public class MetadataExtractor {

    private final AutoDetectParser parser = new AutoDetectParser();

    // Define important metadata keys that should be extracted
    private static final Set<String> IMPORTANT_KEYS = Set.of(
            // Common metadata
            "Content-Type",
            "Last-Modified",
            "Creation-Date",
            "Author",
            "Producer",
            "Creator",
            "Created",
            "Modified",
            "Title",
            "Subject",
            "Description",
            "Keywords",
            "Publisher",

            // Document statistics
            "Page-Count",
            "Word-Count",
            "Character-Count",
            "Paragraph-Count",
            "Line-Count",
            "Slide-Count",

            // PDF specific
            "pdf:PDFVersion",
            "pdf:docinfo:created",
            "pdf:docinfo:creator",
            "pdf:docinfo:producer",
            "pdf:docinfo:modified",
            "xmpTPg:NPages",

            // Office specific
            "Application-Name",
            "Application-Version",
            "Manager",
            "Company",
            "Revision-Number",
            "Template",
            "Total-Time",
            "Edit-Time"
    );

    /**
     * Extracts metadata from a document file.
     *
     * @param filePath The path to the document file
     * @return A map of extracted metadata
     */
    public Map<String, String> extractMetadata(Path filePath) {
        Map<String, String> extractedMetadata = new HashMap<>();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(filePath))) {
            Metadata tikaMetadata = new Metadata();
            ContentHandler handler = new ToTextContentHandler();
            ParseContext context = new ParseContext();

            // Add the filename to metadata
            tikaMetadata.set("resourceName", filePath.getFileName().toString());

            // Parse the document to extract metadata
            parser.parse(input, handler, tikaMetadata, context);

            // Extract and filter metadata
            for (String name : tikaMetadata.names()) {
                if (isImportantMetadata(name)) {
                    String value = tikaMetadata.get(name);
                    if (value != null && !value.isEmpty()) {
                        // Store with a cleaner key name
                        String cleanName = name.replaceAll("^.*:", "").trim();
                        extractedMetadata.put(cleanName, value);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting metadata from file: {}", filePath, e);
        }
        return extractedMetadata;
    }

    /**
     * Determines if a metadata key is important enough to be extracted.
     */
    protected boolean isImportantMetadata(String name) {
        // Check for direct matches with predefined important keys
        if (IMPORTANT_KEYS.contains(name)) {
            return true;
        }

        // Check for partial matches with key metadata words
        String lowerName = name.toLowerCase();
        return lowerName.contains("creator") ||
               lowerName.contains("created") ||
               lowerName.contains("modified") ||
               lowerName.contains("author") ||
               lowerName.contains("producer") ||
               lowerName.contains("title") ||
               lowerName.contains("subject") ||
               lowerName.contains("count") ||
               lowerName.contains("pages") ||
               lowerName.contains("version");
    }
}