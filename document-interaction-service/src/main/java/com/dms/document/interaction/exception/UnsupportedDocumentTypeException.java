package com.dms.document.interaction.exception;

public class UnsupportedDocumentTypeException extends RuntimeException {
    public UnsupportedDocumentTypeException(String message) {
        super(message);
    }
}