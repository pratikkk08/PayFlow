package com.payflow.refunds.domain;

/**
 * Refund lifecycle. {@code PENDING} reserves refundable capacity against the
 * captured payment until the refund becomes {@code SUCCEEDED} or {@code FAILED}.
 */
public enum RefundStatus {
	PENDING,
	SUCCEEDED,
	FAILED
}
