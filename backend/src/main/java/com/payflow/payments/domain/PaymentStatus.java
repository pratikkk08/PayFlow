package com.payflow.payments.domain;

/**
 * Payment lifecycle statuses. {@code CAPTURED} means successful simulated capture,
 * not settlement or payout.
 */
public enum PaymentStatus {
	CREATED,
	AUTHORIZED,
	CAPTURED,
	FAILED,
	CANCELLED
}
