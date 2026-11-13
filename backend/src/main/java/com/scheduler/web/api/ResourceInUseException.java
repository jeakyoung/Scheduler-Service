package com.scheduler.web.api;

/** Thrown when a delete is blocked because something else still references the resource. */
public class ResourceInUseException extends RuntimeException {
    public ResourceInUseException(String message) {
        super(message);
    }
}
