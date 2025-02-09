package com.dms.processor.enums;

import com.dms.processor.exception.UnsupportedDocumentTypeException;
import lombok.Getter;

@Getter
public enum DocumentType {
    PDF("PDF Document") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "application/pdf".equals(mimeType) ||
                    "application/x-pdf".equals(mimeType);
        }
    },

    WORD("Word Document (DOC)") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "application/msword".equals(mimeType) ||
                    "application/vnd.ms-word".equals(mimeType) ||
                    "application/x-msword".equals(mimeType);
        }
    },

    WORD_DOCX("Word Document (DOCX)") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mimeType) ||
                    "application/vnd.ms-word.document.macroenabled.12".equals(mimeType);
        }
    },

    EXCEL("Excel Document (XLS)") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "application/vnd.ms-excel".equals(mimeType) ||
                    "application/msexcel".equals(mimeType) ||
                    "application/x-msexcel".equals(mimeType) ||
                    "application/x-ms-excel".equals(mimeType);
        }
    },

    EXCEL_XLSX("Excel Document (XLSX)") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(mimeType) ||
                    "application/vnd.ms-excel.sheet.macroenabled.12".equals(mimeType);
        }
    },

    POWERPOINT("PowerPoint (PPT)") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "application/vnd.ms-powerpoint".equals(mimeType) ||
                    "application/mspowerpoint".equals(mimeType) ||
                    "application/x-mspowerpoint".equals(mimeType);
        }
    },

    POWERPOINT_PPTX("PowerPoint (PPTX)") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(mimeType) ||
                    "application/vnd.ms-powerpoint.presentation.macroenabled.12".equals(mimeType);
        }
    },

    TEXT_PLAIN("Text Document") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "text/plain".equals(mimeType) ||
                    "text/x-log".equals(mimeType) ||
                    "text/x-java-source".equals(mimeType);
        }
    },

    CSV("CSV Document") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "text/csv".equals(mimeType) ||
                    "text/x-csv".equals(mimeType) ||
                    "application/csv".equals(mimeType) ||
                    "application/x-csv".equals(mimeType);
        }
    },

    XML("XML Document") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "application/xml".equals(mimeType) ||
                    "text/xml".equals(mimeType) ||
                    (mimeType != null && mimeType.endsWith("+xml"));
        }
    },

    JSON("JSON Document") {
        @Override
        protected boolean matchesMimeType(String mimeType) {
            return "application/json".equals(mimeType) ||
                    "application/x-json".equals(mimeType) ||
                    "text/json".equals(mimeType);
        }
    };

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Internal method to match MIME types.
     * Each enum value must implement this to provide its specific MIME type matching logic.
     *
     * @param mimeType the MIME type to check
     * @return true if the MIME type matches this document type
     */
    protected abstract boolean matchesMimeType(String mimeType);

    /**
     * Find the document type for a given MIME type.
     *
     * @param mimeType the MIME type to look up
     * @return the corresponding DocumentType
     * @throws UnsupportedDocumentTypeException if no matching document type is found
     */
    public static DocumentType fromMimeType(String mimeType) {
        if (mimeType == null) {
            throw new UnsupportedDocumentTypeException("MIME type cannot be null");
        }

        for (DocumentType type : values()) {
            if (type.matchesMimeType(mimeType)) {
                return type;
            }
        }
        throw new UnsupportedDocumentTypeException("Unsupported document type: " + mimeType);
    }

    /**
     * Check if a given MIME type is supported by any document type.
     *
     * @param mimeType the MIME type to check
     * @return true if the MIME type is supported by any document type, false otherwise
     */
    public static boolean isSupportedMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }

        for (DocumentType type : values()) {
            if (type.matchesMimeType(mimeType)) {
                return true;
            }
        }
        return false;
    }
}