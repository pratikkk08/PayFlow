package com.payflow.operations;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Index-backed counts only. Partial indexes on PENDING, PROCESSING, and FAILED
 * keep each count bounded to that status.
 */
@Repository
public class EventOperationsSnapshotRepository {

	private static final String OUTBOX_COUNTS = """
			SELECT
			  (SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING') AS pending,
			  (SELECT COUNT(*) FROM outbox_events WHERE status = 'PROCESSING') AS processing,
			  (SELECT COUNT(*) FROM outbox_events WHERE status = 'FAILED') AS failed,
			  (SELECT COUNT(*) FROM outbox_events
			    WHERE status = 'PROCESSING' AND claim_expires_at < ?) AS expired_leases
			""";

	private static final String WEBHOOK_COUNTS = """
			SELECT
			  (SELECT COUNT(*) FROM webhook_deliveries WHERE status = 'PENDING') AS pending,
			  (SELECT COUNT(*) FROM webhook_deliveries WHERE status = 'PROCESSING') AS processing,
			  (SELECT COUNT(*) FROM webhook_deliveries WHERE status = 'FAILED') AS failed,
			  (SELECT COUNT(*) FROM webhook_deliveries
			    WHERE status = 'PROCESSING' AND claim_expires_at < ?) AS expired_leases
			""";

	private final JdbcTemplate jdbcTemplate;

	public EventOperationsSnapshotRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public EventOperationsSnapshot load() {
		Timestamp now = Timestamp.from(Instant.now());
		Counts outbox = jdbcTemplate.queryForObject(OUTBOX_COUNTS, (rs, row) -> new Counts(
				rs.getLong("pending"),
				rs.getLong("processing"),
				rs.getLong("failed"),
				rs.getLong("expired_leases")), now);
		Counts webhooks = jdbcTemplate.queryForObject(WEBHOOK_COUNTS, (rs, row) -> new Counts(
				rs.getLong("pending"),
				rs.getLong("processing"),
				rs.getLong("failed"),
				rs.getLong("expired_leases")), now);
		Counts outboxCounts = outbox == null ? Counts.ZERO : outbox;
		Counts webhookCounts = webhooks == null ? Counts.ZERO : webhooks;
		return new EventOperationsSnapshot(
				outboxCounts.pending,
				outboxCounts.processing,
				outboxCounts.failed,
				outboxCounts.expiredLeases,
				webhookCounts.pending,
				webhookCounts.processing,
				webhookCounts.failed,
				webhookCounts.expiredLeases);
	}

	private record Counts(long pending, long processing, long failed, long expiredLeases) {
		private static final Counts ZERO = new Counts(0, 0, 0, 0);
	}
}
