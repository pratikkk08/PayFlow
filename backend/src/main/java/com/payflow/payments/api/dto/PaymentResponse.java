package com.payflow.payments.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.payflow.payments.domain.PaymentStatus;

public record PaymentResponse(
		UUID paymentId,
		UUID merchantId,
		String merchantReference,
		long amountMinor,
		String currency,
		PaymentStatus status,
		String failureCode,
		Instant authorizedAt,
		Instant capturedAt,
		Instant cancelledAt,
		Instant createdAt) {
}
