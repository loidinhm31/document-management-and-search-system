package com.dms.document.interaction.controller;

import com.dms.document.interaction.exception.DuplicateFavoriteException;
import com.dms.document.interaction.exception.InvalidDocumentException;
import com.dms.document.interaction.exception.UnsupportedDocumentTypeException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidDocument(InvalidDocumentException ex) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedDocumentType(UnsupportedDocumentTypeException ex) {
        return createErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    @ExceptionHandler(DuplicateFavoriteException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateFavorite(DuplicateFavoriteException ex) {
        return createErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidDataAccessResourceUsageException.class)
    public ResponseEntity<Map<String, String>> handleInvalidDataAccess(InvalidDataAccessResourceUsageException ex) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, String>> createErrorResponse(HttpStatus status, String message) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("status", status.toString());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", message);
        return new ResponseEntity<>(errorResponse, status);
    }
}