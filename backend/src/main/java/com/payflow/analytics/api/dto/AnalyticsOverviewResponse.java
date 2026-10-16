package com.payflow.analytics.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Merchant overview aggregates for a UTC reporting window.
 *
 * <p>Status counts ({@code createdCount} … {@code cancelledCount}) are current payment
 * statuses among payments with {@code created_at} in the window — not historical
 * reconstructions. Captured and refund amounts use event timestamps
 * ({@code captured_at}, refund {@code completed_at}). Risk counts use assessment
 * {@code evaluated_at}.
 *
 * <p>{@code paymentCaptureRate} = {@code capturedCount / totalPaymentCount} among
 * payments created in the window; {@code null} when the denominator is zero.
 * Amounts are INR minor units. Not settled bank balances or real revenue.
 */
public record AnalyticsOverviewResponse(
		UUID merchantId,
		String reportingTimezone,
		LocalDate fromInclusive,
		LocalDate toInclusive,
		Instant rangeStartInclusive,
		Instant rangeEndExclusive,
		String currency,
		long totalPaymentCount,
		long createdCount,
		long authorizedCount,
		long capturedCount,
		long failedCount,
		long cancelledCount,
		long totalCapturedAmountMinor,
		long totalSucceededRefundAmountMinor,
		long netCapturedAmountMinor,
		BigDecimal paymentCaptureRate,
		String paymentCaptureRateDefinition,
		long riskBlockCount,
		long riskReviewCount,
		String disclaimer) {
}
