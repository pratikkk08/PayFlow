package com.payflow.analytics.api.dto;

import java.time.LocalDate;

/**
 * One UTC calendar day of event-based metrics.
 *
 * <p>{@code paymentsCreated} buckets by {@code created_at};
 * {@code paymentsCaptured} / {@code capturedAmountMinor} by {@code captured_at}
 * (CAPTURED only); {@code paymentsFailed} by {@code updated_at} for current FAILED
 * rows (no dedicated {@code failed_at}); refunds by SUCCEEDED {@code completed_at}.
 * {@code netAmountMinor} = captured − succeeded refunds for that day (may be negative).
 */
public record AnalyticsDailyPoint(
		LocalDate date,
		long paymentsCreated,
		long paymentsCaptured,
		long paymentsFailed,
		long capturedAmountMinor,
		long succeededRefundAmountMinor,
		long netAmountMinor) {
}
