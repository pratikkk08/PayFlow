package com.payflow.analytics.persistence;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.payflow.analytics.api.dto.AnalyticsDailyPoint;

/**
 * PostgreSQL aggregate queries for merchant analytics. No entity hydration of payment rows.
 */
@Repository
public class MerchantAnalyticsRepository {

	private static final String STATUS_COUNTS = """
			SELECT status, COUNT(*) AS cnt
			FROM payments
			WHERE merchant_id = ?
			  AND created_at >= ?
			  AND created_at < ?
			GROUP BY status
			""";

	private static final String CAPTURED_AMOUNT = """
			SELECT COALESCE(SUM(amount_minor), 0)::bigint AS total
			FROM payments
			WHERE merchant_id = ?
			  AND status = 'CAPTURED'
			  AND captured_at >= ?
			  AND captured_at < ?
			""";

	private static final String SUCCEEDED_REFUND_AMOUNT = """
			SELECT COALESCE(SUM(amount_minor), 0)::bigint AS total
			FROM refunds
			WHERE merchant_id = ?
			  AND status = 'SUCCEEDED'
			  AND completed_at >= ?
			  AND completed_at < ?
			""";

	private static final String RISK_DECISION_COUNTS = """
			SELECT decision, COUNT(*) AS cnt
			FROM risk_assessments
			WHERE merchant_id = ?
			  AND decision IN ('BLOCK', 'REVIEW')
			  AND evaluated_at >= ?
			  AND evaluated_at < ?
			GROUP BY decision
			""";

	private static final String DAILY_SERIES = """
			WITH days AS (
			  SELECT generate_series(?::date, ?::date, INTERVAL '1 day')::date AS day
			),
			created AS (
			  SELECT (created_at AT TIME ZONE 'UTC')::date AS day, COUNT(*)::bigint AS cnt
			  FROM payments
			  WHERE merchant_id = ?
			    AND created_at >= ?
			    AND created_at < ?
			  GROUP BY 1
			),
			captured AS (
			  SELECT (captured_at AT TIME ZONE 'UTC')::date AS day,
			         COUNT(*)::bigint AS cnt,
			         COALESCE(SUM(amount_minor), 0)::bigint AS amt
			  FROM payments
			  WHERE merchant_id = ?
			    AND status = 'CAPTURED'
			    AND captured_at >= ?
			    AND captured_at < ?
			  GROUP BY 1
			),
			failed AS (
			  SELECT (updated_at AT TIME ZONE 'UTC')::date AS day, COUNT(*)::bigint AS cnt
			  FROM payments
			  WHERE merchant_id = ?
			    AND status = 'FAILED'
			    AND updated_at >= ?
			    AND updated_at < ?
			  GROUP BY 1
			),
			refunded AS (
			  SELECT (completed_at AT TIME ZONE 'UTC')::date AS day,
			         COALESCE(SUM(amount_minor), 0)::bigint AS amt
			  FROM refunds
			  WHERE merchant_id = ?
			    AND status = 'SUCCEEDED'
			    AND completed_at >= ?
			    AND completed_at < ?
			  GROUP BY 1
			)
			SELECT d.day AS report_date,
			       COALESCE(c.cnt, 0) AS payments_created,
			       COALESCE(cap.cnt, 0) AS payments_captured,
			       COALESCE(f.cnt, 0) AS payments_failed,
			       COALESCE(cap.amt, 0) AS captured_amount_minor,
			       COALESCE(r.amt, 0) AS succeeded_refund_amount_minor,
			       COALESCE(cap.amt, 0) - COALESCE(r.amt, 0) AS net_amount_minor
			FROM days d
			LEFT JOIN created c ON c.day = d.day
			LEFT JOIN captured cap ON cap.day = d.day
			LEFT JOIN failed f ON f.day = d.day
			LEFT JOIN refunded r ON r.day = d.day
			ORDER BY d.day ASC
			""";

	private final JdbcTemplate jdbcTemplate;

	public MerchantAnalyticsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Map<String, Long> countPaymentStatusesByCreatedAt(
			UUID merchantId, Instant rangeStartInclusive, Instant rangeEndExclusive) {
		Timestamp start = Timestamp.from(rangeStartInclusive);
		Timestamp end = Timestamp.from(rangeEndExclusive);
		Map<String, Long> counts = new HashMap<>();
		jdbcTemplate.query(STATUS_COUNTS, rs -> {
			counts.put(rs.getString("status"), rs.getLong("cnt"));
		}, merchantId, start, end);
		return counts;
	}

	public long sumCapturedAmountMinor(
			UUID merchantId, Instant rangeStartInclusive, Instant rangeEndExclusive) {
		Long total = jdbcTemplate.queryForObject(
				CAPTURED_AMOUNT,
				Long.class,
				merchantId,
				Timestamp.from(rangeStartInclusive),
				Timestamp.from(rangeEndExclusive));
		return total == null ? 0L : total;
	}

	public long sumSucceededRefundAmountMinor(
			UUID merchantId, Instant rangeStartInclusive, Instant rangeEndExclusive) {
		Long total = jdbcTemplate.queryForObject(
				SUCCEEDED_REFUND_AMOUNT,
				Long.class,
				merchantId,
				Timestamp.from(rangeStartInclusive),
				Timestamp.from(rangeEndExclusive));
		return total == null ? 0L : total;
	}

	public Map<String, Long> countRiskDecisionsByEvaluatedAt(
			UUID merchantId, Instant rangeStartInclusive, Instant rangeEndExclusive) {
		Timestamp start = Timestamp.from(rangeStartInclusive);
		Timestamp end = Timestamp.from(rangeEndExclusive);
		Map<String, Long> counts = new HashMap<>();
		jdbcTemplate.query(RISK_DECISION_COUNTS, rs -> {
			counts.put(rs.getString("decision"), rs.getLong("cnt"));
		}, merchantId, start, end);
		return counts;
	}

	public List<AnalyticsDailyPoint> loadDailySeries(
			UUID merchantId,
			LocalDate fromInclusive,
			LocalDate toInclusive,
			Instant rangeStartInclusive,
			Instant rangeEndExclusive) {
		Timestamp start = Timestamp.from(rangeStartInclusive);
		Timestamp end = Timestamp.from(rangeEndExclusive);
		Date from = Date.valueOf(fromInclusive);
		Date to = Date.valueOf(toInclusive);
		return jdbcTemplate.query(
				DAILY_SERIES,
				(rs, rowNum) -> new AnalyticsDailyPoint(
						rs.getDate("report_date").toLocalDate(),
						rs.getLong("payments_created"),
						rs.getLong("payments_captured"),
						rs.getLong("payments_failed"),
						rs.getLong("captured_amount_minor"),
						rs.getLong("succeeded_refund_amount_minor"),
						rs.getLong("net_amount_minor")),
				from,
				to,
				merchantId, start, end,
				merchantId, start, end,
				merchantId, start, end,
				merchantId, start, end);
	}
}
