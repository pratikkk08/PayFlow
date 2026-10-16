package com.payflow.analytics.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Half-open UTC instant range derived from inclusive calendar dates.
 *
 * <p>{@code fromInclusive} and {@code toInclusive} are UTC calendar days.
 * Instant bounds are {@code [from 00:00:00Z, (to+1) 00:00:00Z)}.
 */
public record AnalyticsReportingWindow(
		LocalDate fromInclusive,
		LocalDate toInclusive,
		Instant rangeStartInclusive,
		Instant rangeEndExclusive) {

	public static final String REPORTING_TIMEZONE = "UTC";

	public static AnalyticsReportingWindow ofInclusiveDates(LocalDate fromInclusive, LocalDate toInclusive) {
		Instant start = fromInclusive.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant endExclusive = toInclusive.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		return new AnalyticsReportingWindow(fromInclusive, toInclusive, start, endExclusive);
	}

	public long inclusiveDayCount() {
		return ChronoUnit.DAYS.between(fromInclusive, toInclusive) + 1L;
	}
}
