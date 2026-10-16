package com.payflow.outbox.domain;

public enum OutboxEventStatus {
	PENDING,
	PROCESSING,
	PUBLISHED,
	FAILED
}
