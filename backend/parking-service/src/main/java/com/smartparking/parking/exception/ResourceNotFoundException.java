package com.smartparking.parking.exception;

/**
 * Thrown when a referenced resource (gate, slot, session, …) does not exist.
 * Maps to HTTP 404 for REST callers via {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final String identifier;

    public ResourceNotFoundException(String resource, String identifier) {
        super("%s not found: %s".formatted(resource, identifier));
        this.resource = resource;
        this.identifier = identifier;
    }

    public String getResource() {
        return resource;
    }

    public String getIdentifier() {
        return identifier;
    }
}
