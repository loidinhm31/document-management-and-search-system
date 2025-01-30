package com.dms.document.search.utils;

import com.dms.document.search.enums.DocumentType;
import com.dms.document.search.exception.UnsupportedDocumentTypeException;

public class DocumentUtils {
    public static DocumentType determineDocumentType(String mimeType) {
        try {
            return DocumentType.fromMimeType(mimeType);
        } catch (UnsupportedDocumentTypeException e) {
            throw new UnsupportedDocumentTypeException("Unsupported document type: " + mimeType);
        }
    }
}
