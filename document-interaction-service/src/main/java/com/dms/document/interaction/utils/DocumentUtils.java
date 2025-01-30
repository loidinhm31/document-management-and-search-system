package com.dms.document.interaction.utils;

import com.dms.document.interaction.enums.DocumentType;
import com.dms.document.interaction.exception.UnsupportedDocumentTypeException;

public class DocumentUtils {
    public static DocumentType determineDocumentType(String mimeType) {
        try {
            return DocumentType.fromMimeType(mimeType);
        } catch (UnsupportedDocumentTypeException e) {
            throw new UnsupportedDocumentTypeException("Unsupported document type: " + mimeType);
        }
    }
}
