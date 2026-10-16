package com.payflow.outbox.domain;

/**
 * Stable aggregate type identifiers for outbox rows and future Kafka topics.
 */
public enum OutboxAggregateType {
	PAYMENT,
	REFUND
}
