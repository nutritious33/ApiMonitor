package com.example.apimonitor.exception;

public class TooManyEndpointsException extends RuntimeException {
    public TooManyEndpointsException(String message) {
        super(message);
    }
}
