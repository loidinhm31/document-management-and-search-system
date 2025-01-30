package com.dms.document.interaction.enums;

public enum DocumentStatus {
    PENDING,      // Initial state when document is uploaded
    PROCESSING,   // Document is being processed (OCR, content extraction)
    COMPLETED,    // Processing completed successfully
    FAILED        // Processing failed
}