package com.sdms.document.utils;

import com.sdms.document.enums.DocumentType;

public class DocumentUtils {
    public static DocumentType determineDocumentType(String mimeType) {
        if (mimeType == null) return DocumentType.OTHER;

        String type = mimeType.toLowerCase();

        if (type.equals("application/pdf")) {
            return DocumentType.PDF;
        }

        if (type.equals("application/msword") ||
                type.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            return DocumentType.WORD;
        }

        if (type.equals("application/vnd.ms-excel") ||
                type.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            return DocumentType.EXCEL;
        }

        if (type.equals("application/vnd.ms-powerpoint") ||
                type.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
            return DocumentType.POWERPOINT;
        }

        if (type.startsWith("text/")) {
            return DocumentType.TEXT;
        }

        if (type.startsWith("image/")) {
            return DocumentType.IMAGE;
        }

        return DocumentType.OTHER;
    }
}
