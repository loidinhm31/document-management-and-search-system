package com.dms.document.utils;

import com.dms.document.enums.DocumentType;
import com.dms.document.exception.UnsupportedDocumentTypeException;

public class DocumentUtils {
    public static DocumentType determineDocumentType(String mimeType) {
        try {
            return DocumentType.fromMimeType(mimeType);
        } catch (UnsupportedDocumentTypeException e) {
            throw new UnsupportedDocumentTypeException("Unsupported document type: " + mimeType);
        }
    }
}
