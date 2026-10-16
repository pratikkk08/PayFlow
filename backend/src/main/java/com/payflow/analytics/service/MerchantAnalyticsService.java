package com.payflow.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.analytics.api.dto.AnalyticsDailyPoint;
import com.payflow.analytics.api.dto.AnalyticsDailyResponse;
import com.payflow.analytics.api.dto.AnalyticsOverviewResponse;
import com.payflow.analytics.persistence.MerchantAnalyticsRepository;
import com.payflow.merchants.service.MerchantService;
import com.payflow.shared.error.ApiException;

@Service
public class MerchantAnalyticsService {

	public static final int DEFAULT_INCLUSIVE_DAYS = 30;
	public static final int MAX_INCLUSIVE_DAYS = 366;
	public static final String CURRENCY = "INR";
	public static final String CAPTURE_RATE_DEFINITION =
			"capturedCount / totalPaymentCount for payments with created_at in the reporting window "
					+ "(current CAPTURED status); null when totalPaymentCount is 0";
	public static final String DISCLAIMER =
			"Sandbox activity metrics only — not settled bank balances, payouts, or real revenue. "
					+ "Status counts reflect current payment status; historical transitions are not reconstructed.";

	private final MerchantService merchantService;
	private final MerchantAnalyticsRepository analyticsRepository;
	private final Clock clock;

	public MerchantAnalyticsService(
			MerchantService merchantService,
			MerchantAnalyticsRepository analyticsRepository,
			Clock clock) {
		this.merchantService = merchantService;
		this.analyticsRepository = analyticsRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AnalyticsOverviewResponse overview(UUID userId, UUID merchantId, LocalDate from, LocalDate to) {
		merchantService.requireOwner(userId, merchantId);
		AnalyticsReportingWindow window = resolveWindow(from, to);

		Map<String, Long> statusCounts = analyticsRepository.countPaymentStatusesByCreatedAt(
				merchantId, window.rangeStartInclusive(), window.rangeEndExclusive());
		long created = statusCounts.getOrDefault("CREATED", 0L);
		long authorized = statusCounts.getOrDefault("AUTHORIZED", 0L);
		long captured = statusCounts.getOrDefault("CAPTURED", 0L);
		long failed = statusCounts.getOrDefault("FAILED", 0L);
		long cancelled = statusCounts.getOrDefault("CANCELLED", 0L);
		long total = created + authorized + captured + failed + cancelled;

		long capturedAmount = analyticsRepository.sumCapturedAmountMinor(
				merchantId, window.rangeStartInclusive(), window.rangeEndExclusive());
		long refundAmount = analyticsRepository.sumSucceededRefundAmountMinor(
				merchantId, window.rangeStartInclusive(), window.rangeEndExclusive());

		Map<String, Long> riskCounts = analyticsRepository.countRiskDecisionsByEvaluatedAt(
				merchantId, window.rangeStartInclusive(), window.rangeEndExclusive());

		return new AnalyticsOverviewResponse(
				merchantId,
				AnalyticsReportingWindow.REPORTING_TIMEZONE,
				window.fromInclusive(),
				window.toInclusive(),
				window.rangeStartInclusive(),
				window.rangeEndExclusive(),
				CURRENCY,
				total,
				created,
				authorized,
				captured,
				failed,
				cancelled,
				capturedAmount,
				refundAmount,
				capturedAmount - refundAmount,
				captureRate(captured, total),
				CAPTURE_RATE_DEFINITION,
				riskCounts.getOrDefault("BLOCK", 0L),
				riskCounts.getOrDefault("REVIEW", 0L),
				DISCLAIMER);
	}

	@Transactional(readOnly = true)
	public AnalyticsDailyResponse daily(UUID userId, UUID merchantId, LocalDate from, LocalDate to) {
		merchantService.requireOwner(userId, merchantId);
		AnalyticsReportingWindow window = resolveWindow(from, to);

		List<AnalyticsDailyPoint> days = analyticsRepository.loadDailySeries(
				merchantId,
				window.fromInclusive(),
				window.toInclusive(),
				window.rangeStartInclusive(),
				window.rangeEndExclusive());

		return new AnalyticsDailyResponse(
				merchantId,
				AnalyticsReportingWindow.REPORTING_TIMEZONE,
				window.fromInclusive(),
				window.toInclusive(),
				window.rangeStartInclusive(),
				window.rangeEndExclusive(),
				CURRENCY,
				days,
				DISCLAIMER);
	}

	AnalyticsReportingWindow resolveWindow(LocalDate from, LocalDate to) {
		LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		LocalDate resolvedTo = to != null ? to : todayUtc;
		LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_INCLUSIVE_DAYS - 1L);

		if (resolvedFrom.isAfter(resolvedTo)) {
			throw invalidRange("from must be on or before to (UTC calendar dates, inclusive)");
		}

		AnalyticsReportingWindow window = AnalyticsReportingWindow.ofInclusiveDates(resolvedFrom, resolvedTo);
		if (window.inclusiveDayCount() > MAX_INCLUSIVE_DAYS) {
			throw invalidRange("Reporting range must not exceed " + MAX_INCLUSIVE_DAYS + " inclusive UTC days");
		}
		return window;
	}

	private static BigDecimal captureRate(long capturedCount, long totalPaymentCount) {
		if (totalPaymentCount == 0L) {
			return null;
		}
		return BigDecimal.valueOf(capturedCount)
				.divide(BigDecimal.valueOf(totalPaymentCount), 6, RoundingMode.HALF_UP);
	}

	private static ApiException invalidRange(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "invalid_reporting_range", message);
	}
}
