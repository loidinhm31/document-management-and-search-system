package com.sdms.document.enums;

import com.sdms.document.exception.UnsupportedDocumentTypeException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DocumentType {
    PDF("application/pdf", "PDF Document"),
    WORD("application/msword", "Word Document (DOC)"),
    WORD_DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word Document (DOCX)"),
    EXCEL("application/vnd.ms-excel", "Excel Document (XLS)"),
    EXCEL_XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel Document (XLSX)"),
    POWERPOINT("application/vnd.ms-powerpoint", "PowerPoint (PPT)"),
    POWERPOINT_PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint (PPTX)"),
    TEXT_PLAIN("text/plain", "Text Document"),
    RTF("application/rtf", "Rich Text Format"),
    CSV("text/csv", "CSV Document"),
    XML("application/xml", "XML Document"),
    JSON("application/json", "JSON Document");

    private final String mimeType;
    private final String displayName;

    public static DocumentType fromMimeType(String mimeType) {
        for (DocumentType type : values()) {
            if (type.getMimeType().equals(mimeType)) {
                return type;
            }
        }
        throw new UnsupportedDocumentTypeException("Unsupported document type: " + mimeType);
    }

    public static boolean isSupportedMimeType(String mimeType) {
        for (DocumentType type : values()) {
            if (type.getMimeType().equals(mimeType)) {
                return true;
            }
        }
        return false;
    }
}

