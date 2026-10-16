package com.payflow.outbox.consume;

/**
 * Non-retryable validation failure. The Kafka error handler routes these to the DLT
 * without further delivery attempts.
 */
public class InvalidDomainEventException extends RuntimeException {

	public InvalidDomainEventException(String message) {
		super(message);
	}

	public InvalidDomainEventException(String message, Throwable cause) {
		super(message, cause);
	}
}
