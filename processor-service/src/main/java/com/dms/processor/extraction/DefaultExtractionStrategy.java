package com.dms.processor.extraction;

import com.dms.processor.dto.DocumentExtractContent;
import com.dms.processor.exception.DocumentProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

/**
 * Default extraction strategy that handles any document type as a fallback.
 * This strategy has the lowest order to ensure it's selected only when no other strategy matches.
 */
@Component
@Order() // Lowest priority
@Slf4j
@RequiredArgsConstructor
public class DefaultExtractionStrategy implements DocumentExtractionStrategy {

  private final TikaExtractor tikaExtractor;
  private final MetadataExtractor metadataExtractor;

  @Override
  public boolean canHandle(Path filePath, String mimeType) {
    // Fallback strategy that handles any document type
    return true;
  }

  @Override
  public DocumentExtractContent extract(Path filePath, String mimeType, Map<String, String> metadata) throws DocumentProcessingException {
    log.info("Using default extraction strategy for file: {} with MIME type: {}",
            filePath.getFileName(), mimeType);

    try {
      // Extract metadata
      metadata.putAll(metadataExtractor.extractMetadata(filePath));

      // Try to extract content with Tika (no OCR)
      String content = tikaExtractor.extractTextContent(filePath, metadata, true);

      // If content extraction failed, try with OCR enabled
      if (content == null || content.trim().isEmpty()) {
        log.info("Extraction attempt yielded no content, trying with OCR enabled");
        content = tikaExtractor.extractTextContent(filePath, metadata, false);
      }

      return new DocumentExtractContent(content, metadata);
    } catch (Exception e) {
      log.error("Error in default extraction for file: {}", filePath.getFileName(), e);
      throw new DocumentProcessingException("Default extraction failed", e);
    }
  }
}