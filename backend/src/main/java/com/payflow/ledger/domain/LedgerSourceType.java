package com.payflow.ledger.domain;

/**
 * Domain event that originated a ledger transaction. Unique with {@code sourceId}
 * to prevent duplicate postings.
 */
public enum LedgerSourceType {
	PAYMENT_CAPTURE,
	REFUND
}
