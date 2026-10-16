package com.payflow.analytics.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Daily event series for a UTC reporting window (one row per inclusive calendar day;
 * no pagination — aggregated result set is bounded by the max reporting range).
 */
public record AnalyticsDailyResponse(
		UUID merchantId,
		String reportingTimezone,
		LocalDate fromInclusive,
		LocalDate toInclusive,
		Instant rangeStartInclusive,
		Instant rangeEndExclusive,
		String currency,
		List<AnalyticsDailyPoint> days,
		String disclaimer) {
}
