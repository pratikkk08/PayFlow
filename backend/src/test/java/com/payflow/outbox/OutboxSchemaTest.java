package com.payflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.payflow.TestcontainersConfiguration;
import com.payflow.outbox.domain.OutboxAggregateType;
import com.payflow.outbox.domain.OutboxEventEntity;
import com.payflow.outbox.domain.OutboxEventStatus;
import com.payflow.outbox.domain.OutboxEventType;
import com.payflow.outbox.persistence.OutboxEventRepository;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxSchemaTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Test
	void outboxTableExistsWithExpectedConstraintsAndIndexes() {
		Integer tables = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.tables
						WHERE table_schema = 'public' AND table_name = 'outbox_events'
						""",
				Integer.class);
		assertThat(tables).isEqualTo(1);

		assertThat(checkConstraint("ck_outbox_events_status")).isEqualTo(1);
		assertThat(checkConstraint("ck_outbox_events_status_shape")).isEqualTo(1);
		assertThat(checkConstraint("ck_outbox_events_schema_version")).isEqualTo(1);
		assertThat(checkConstraint("ck_outbox_events_attempt_count")).isEqualTo(1);

		assertThat(indexExists("idx_outbox_events_pending_poll")).isEqualTo(1);
		assertThat(indexExists("idx_outbox_events_stale_claims")).isEqualTo(1);
		assertThat(indexExists("idx_outbox_events_failed")).isEqualTo(1);
		assertThat(indexExists("idx_outbox_events_aggregate")).isEqualTo(1);
		assertThat(indexExists("idx_outbox_events_merchant_created")).isEqualTo(1);

		String payloadType = jdbcTemplate.queryForObject(
				"""
						SELECT data_type FROM information_schema.columns
						WHERE table_name = 'outbox_events' AND column_name = 'payload'
						""",
				String.class);
		assertThat(payloadType).isEqualTo("jsonb");
	}

	@Test
	void acceptsValidPendingRowAndRejectsInvalidShapes() {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		String email = "outbox-schema-" + userId + "@example.com";

		jdbcTemplate.update(
				"""
						INSERT INTO users (id, full_name, email, email_normalized, password_hash)
						VALUES (?, 'Outbox Schema', ?, ?, 'hash')
						""",
				userId, email, email);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Outbox Merchant', 'ACTIVE', ?)
						""",
				merchantId, userId);

		jdbcTemplate.update(
				"""
						INSERT INTO outbox_events (
						  id, aggregate_type, aggregate_id, event_type, event_schema_version,
						  merchant_id, payload, status, attempt_count, next_attempt_at, created_at)
						VALUES (?, 'PAYMENT', ?, 'payment.captured', 1, ?, CAST(? AS jsonb),
						        'PENDING', 0, NOW(), NOW())
						""",
				eventId, paymentId, merchantId, "{\"paymentId\":\"" + paymentId + "\",\"amountMinor\":1000}");

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM outbox_events WHERE id = ?", Integer.class, eventId);
		assertThat(count).isEqualTo(1);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO outbox_events (
						  id, aggregate_type, aggregate_id, event_type, event_schema_version,
						  payload, status, attempt_count, next_attempt_at, created_at, published_at)
						VALUES (?, 'PAYMENT', ?, 'payment.captured', 1, CAST('{}' AS jsonb),
						        'PENDING', 0, NOW(), NOW(), NOW())
						""",
				UUID.randomUUID(), paymentId))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO outbox_events (
						  id, aggregate_type, aggregate_id, event_type, event_schema_version,
						  payload, status, attempt_count, next_attempt_at, created_at)
						VALUES (?, 'PAYMENT', ?, 'payment.captured', 0, CAST('{}' AS jsonb),
						        'PENDING', 0, NOW(), NOW())
						""",
				UUID.randomUUID(), paymentId))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO outbox_events (
						  id, aggregate_type, aggregate_id, event_type, event_schema_version,
						  payload, status, attempt_count, next_attempt_at, created_at)
						VALUES (?, 'PAYMENT', ?, 'payment.captured', 1, CAST('{}' AS jsonb),
						        'BOGUS', 0, NOW(), NOW())
						""",
				UUID.randomUUID(), paymentId))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO outbox_events (
						  id, aggregate_type, aggregate_id, event_type, event_schema_version,
						  payload, status, attempt_count, next_attempt_at, created_at, claim_expires_at)
						VALUES (?, 'PAYMENT', ?, 'payment.captured', 1, CAST('{}' AS jsonb),
						        'PROCESSING', 1, NOW(), NOW(), NULL)
						""",
				UUID.randomUUID(), paymentId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void jpaEntityRoundTripAndPollingQueriesMatchSchema() {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String email = "outbox-jpa-" + userId + "@example.com";
		Instant now = Instant.parse("2026-03-01T12:00:00Z");

		jdbcTemplate.update(
				"""
						INSERT INTO users (id, full_name, email, email_normalized, password_hash)
						VALUES (?, 'Outbox JPA', ?, ?, 'hash')
						""",
				userId, email, email);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Outbox JPA Merchant', 'ACTIVE', ?)
						""",
				merchantId, userId);

		UUID dueId = UUID.randomUUID();
		UUID futureId = UUID.randomUUID();
		UUID staleClaimId = UUID.randomUUID();
		UUID failedId = UUID.randomUUID();

		outboxEventRepository.saveAndFlush(OutboxEventEntity.pending(
				dueId, OutboxAggregateType.PAYMENT, paymentId, OutboxEventType.PAYMENT_CAPTURED,
				1, merchantId, "{\"paymentId\":\"" + paymentId + "\",\"amountMinor\":2500}", now));

		OutboxEventEntity future = OutboxEventEntity.pending(
				futureId, OutboxAggregateType.PAYMENT, paymentId, OutboxEventType.PAYMENT_AUTHORIZED,
				1, merchantId, "{\"paymentId\":\"" + paymentId + "\"}", now.plusSeconds(3600));
		outboxEventRepository.saveAndFlush(future);

		jdbcTemplate.update(
				"""
						INSERT INTO outbox_events (
						  id, aggregate_type, aggregate_id, event_type, event_schema_version,
						  merchant_id, payload, status, attempt_count, next_attempt_at,
						  created_at, claim_expires_at, claim_token, last_error)
						VALUES (?, 'PAYMENT', ?, 'payment.failed', 1, ?, CAST(? AS jsonb),
						        'PROCESSING', 2, ?, ?, ?, ?, 'simulated timeout')
						""",
				staleClaimId, paymentId, merchantId, "{\"paymentId\":\"" + paymentId + "\"}",
				java.sql.Timestamp.from(now.minusSeconds(60)),
				java.sql.Timestamp.from(now.minusSeconds(120)),
				java.sql.Timestamp.from(now.minusSeconds(30)),
				UUID.randomUUID());

		jdbcTemplate.update(
				"""
						INSERT INTO outbox_events (
						  id, aggregate_type, aggregate_id, event_type, event_schema_version,
						  merchant_id, payload, status, attempt_count, next_attempt_at,
						  created_at, last_error)
						VALUES (?, 'REFUND', ?, 'refund.failed', 1, ?, CAST('{\"reason\":\"exhausted\"}' AS jsonb),
						        'FAILED', 10, ?, ?, 'publish exhausted')
						""",
				failedId, UUID.randomUUID(), merchantId,
				java.sql.Timestamp.from(now),
				java.sql.Timestamp.from(now.minusSeconds(10)));

		OutboxEventEntity loaded = outboxEventRepository.findById(dueId).orElseThrow();
		assertThat(loaded.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(loaded.getEventType()).isEqualTo("payment.captured");
		assertThat(loaded.getAggregateType()).isEqualTo("PAYMENT");
		assertThat(loaded.getPayload()).contains("amountMinor");
		assertThat(loaded.getAttemptCount()).isZero();
		assertThat(loaded.getClaimExpiresAt()).isNull();
		assertThat(loaded.getPublishedAt()).isNull();

		List<OutboxEventEntity> pendingDue = outboxEventRepository.findPendingDue(now.plusSeconds(1),
				PageRequest.of(0, 10));
		assertThat(pendingDue).extracting(OutboxEventEntity::getId).contains(dueId).doesNotContain(futureId);

		List<OutboxEventEntity> stale = outboxEventRepository.findExpiredClaims(now, PageRequest.of(0, 10));
		assertThat(stale).extracting(OutboxEventEntity::getId).contains(staleClaimId);

		List<OutboxEventEntity> failed = outboxEventRepository.findByStatusOrderByCreatedAtDescIdDesc(
				OutboxEventStatus.FAILED, PageRequest.of(0, 10));
		assertThat(failed).extracting(OutboxEventEntity::getId).contains(failedId);
		assertThat(failed.getFirst().getLastError()).isEqualTo("publish exhausted");
	}

	private Integer checkConstraint(String name) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.check_constraints
						WHERE constraint_name = ?
						""",
				Integer.class, name);
	}

	private Integer indexExists(String name) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM pg_indexes
						WHERE tablename = 'outbox_events' AND indexname = ?
						""",
				Integer.class, name);
	}
}
