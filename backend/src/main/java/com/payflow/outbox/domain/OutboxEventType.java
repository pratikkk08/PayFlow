package com.payflow.outbox.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Stable event type identifiers (schema version starts at 1 per type).
 */
public enum OutboxEventType {
	PAYMENT_CREATED("payment.created"),
	PAYMENT_AUTHORIZED("payment.authorized"),
	PAYMENT_CAPTURED("payment.captured"),
	PAYMENT_CANCELLED("payment.cancelled"),
	PAYMENT_FAILED("payment.failed"),
	REFUND_SUCCEEDED("refund.succeeded"),
	REFUND_FAILED("refund.failed");

	private final String wireValue;

	OutboxEventType(String wireValue) {
		this.wireValue = wireValue;
	}

	public String wireValue() {
		return wireValue;
	}

	public static Optional<OutboxEventType> fromWireValue(String wireValue) {
		if (wireValue == null || wireValue.isBlank()) {
			return Optional.empty();
		}
		return Arrays.stream(values())
				.filter(type -> type.wireValue.equals(wireValue))
				.findFirst();
	}

	public boolean isPaymentEvent() {
		return wireValue.startsWith("payment.");
	}

	public boolean isRefundEvent() {
		return wireValue.startsWith("refund.");
	}
}
