package com.payflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.payflow.TestcontainersConfiguration;
import com.payflow.outbox.publish.OutboxPublisher;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxPublisherRetryTest {

	@DynamicPropertySource
	static void enablePublisher(DynamicPropertyRegistry registry) {
		registry.add("payflow.kafka.enabled", () -> "true");
		registry.add("payflow.outbox.publisher.enabled", () -> "true");
		registry.add("payflow.outbox.publisher.scheduling-enabled", () -> "false");
		registry.add("payflow.outbox.publisher.max-attempts", () -> "3");
		registry.add("payflow.outbox.publisher.backoff-initial-ms", () -> "1000");
		registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
	}

	@MockitoBean
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private OutboxPublisher outboxPublisher;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void resetMock() {
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker_unavailable")));
	}

	@Test
	void transientKafkaFailureSchedulesBackoffThenMarksFailed() {
		UUID merchantId = seedMerchant();
		UUID eventId = insertPending(merchantId, Instant.now());

		outboxPublisher.publishOnce();
		Map<String, Object> afterFirst = row(eventId);
		assertThat(afterFirst.get("status")).isEqualTo("PENDING");
		assertThat(((Number) afterFirst.get("attempt_count")).intValue()).isEqualTo(1);
		assertThat(afterFirst.get("claim_token")).isNull();
		assertThat(afterFirst.get("last_error").toString()).contains("broker_unavailable");
		Instant next1 = ((java.sql.Timestamp) afterFirst.get("next_attempt_at")).toInstant();
		assertThat(next1).isAfter(Instant.now().minusSeconds(1));

		// Make due again
		jdbcTemplate.update("UPDATE outbox_events SET next_attempt_at = NOW() - INTERVAL '1 second' WHERE id = ?",
				eventId);
		outboxPublisher.publishOnce();
		assertThat(row(eventId).get("status")).isEqualTo("PENDING");
		assertThat(((Number) row(eventId).get("attempt_count")).intValue()).isEqualTo(2);

		jdbcTemplate.update("UPDATE outbox_events SET next_attempt_at = NOW() - INTERVAL '1 second' WHERE id = ?",
				eventId);
		outboxPublisher.publishOnce();
		Map<String, Object> exhausted = row(eventId);
		assertThat(exhausted.get("status")).isEqualTo("FAILED");
		assertThat(((Number) exhausted.get("attempt_count")).intValue()).isEqualTo(3);
		assertThat(exhausted.get("claim_token")).isNull();
	}

	@Test
	void brokerAckWithLostClaimRemainsReplayableAsProcessingOrPending() {
		UUID merchantId = seedMerchant();
		UUID eventId = insertPending(merchantId, Instant.now());

		CompletableFuture<SendResult<String, String>> ok = new CompletableFuture<>();
		ok.complete(null);
		when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
			// After ACK path, corrupt claim so markPublished no-ops.
			jdbcTemplate.update(
					"UPDATE outbox_events SET claim_token = ? WHERE id = ? AND status = 'PROCESSING'",
					UUID.randomUUID(), eventId);
			return ok;
		});

		outboxPublisher.publishOnce();

		Map<String, Object> after = row(eventId);
		assertThat(after.get("status")).isEqualTo("PROCESSING");
		assertThat(after.get("status")).isNotEqualTo("PUBLISHED");
	}

	private Map<String, Object> row(UUID eventId) {
		return jdbcTemplate.queryForMap("SELECT * FROM outbox_events WHERE id = ?", eventId);
	}

	private UUID insertPending(UUID merchantId, Instant createdAt) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update(
				"""
						INSERT INTO outbox_events (
						  id, aggregate_type, aggregate_id, event_type, event_schema_version,
						  merchant_id, payload, status, attempt_count, next_attempt_at, created_at)
						VALUES (?, 'PAYMENT', ?, 'payment.created', 1, ?, CAST('{}' AS jsonb),
						        'PENDING', 0, ?, ?)
						""",
				id, UUID.randomUUID(), merchantId,
				java.sql.Timestamp.from(createdAt),
				java.sql.Timestamp.from(createdAt));
		return id;
	}

	private UUID seedMerchant() {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		String email = "retry-" + userId + "@example.com";
		jdbcTemplate.update(
				"""
						INSERT INTO users (id, full_name, email, email_normalized, password_hash)
						VALUES (?, 'Retry', ?, ?, 'hash')
						""",
				userId, email, email);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Retry Merchant', 'ACTIVE', ?)
						""",
				merchantId, userId);
		return merchantId;
	}
}
