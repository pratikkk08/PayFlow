package com.payflow.refunds.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.payflow.refunds.domain.RefundStatus;

public record RefundResponse(
		UUID refundId,
		UUID paymentId,
		UUID merchantId,
		String merchantReference,
		long amountMinor,
		String currency,
		RefundStatus status,
		String failureCode,
		Instant completedAt,
		Instant createdAt,
		Instant updatedAt) {
}
