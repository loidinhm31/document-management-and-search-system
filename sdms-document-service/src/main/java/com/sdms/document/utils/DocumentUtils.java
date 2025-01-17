package com.sdms.document.utils;

import com.sdms.document.enums.DocumentType;
import com.sdms.document.exception.UnsupportedDocumentTypeException;

public class DocumentUtils {
    public static DocumentType determineDocumentType(String mimeType) {
        try {
            return DocumentType.fromMimeType(mimeType);
        } catch (UnsupportedDocumentTypeException e) {
            throw new UnsupportedDocumentTypeException("Unsupported document type: " + mimeType);
        }
    }
}
