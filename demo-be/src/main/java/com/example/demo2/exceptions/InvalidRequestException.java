package com.example.demo2.exceptions;

public class InvalidRequestException extends Exception {
    public InvalidRequestException(String message) {
        super(message);
    }
}