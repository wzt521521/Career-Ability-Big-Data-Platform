package com.career.platform.collect.service;

/** A transient collection failure that can be retried using the task backoff policy. */
public class RetryableCollectTaskException extends RuntimeException {

    public RetryableCollectTaskException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetryableCollectTaskException(String message) {
        super(message);
    }
}
