package com.example.demo2.exceptions;

public class OAuth2RegistrationException extends RuntimeException {
    public OAuth2RegistrationException(String message) {
        super(message);
    }

    public OAuth2RegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}