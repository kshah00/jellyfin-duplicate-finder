package com.jellydupes.service;

import java.util.List;

/** Bad user input; the API layer maps it to HTTP 400. */
public class ValidationException extends RuntimeException {

    private final List<String> details;

    public ValidationException(String message) {
        this(message, List.of());
    }

    public ValidationException(String message, List<String> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    public List<String> details() {
        return details;
    }
}
