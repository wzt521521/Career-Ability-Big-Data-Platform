package com.career.platform.collect.service;

/** A deterministic source/configuration failure that must not be retried. */
public class NonRetryableCollectTaskException extends RuntimeException {

    public NonRetryableCollectTaskException(String message, Throwable cause) {
        super(message, cause);
    }

    public NonRetryableCollectTaskException(String message) {
        super(message);
    }
}
