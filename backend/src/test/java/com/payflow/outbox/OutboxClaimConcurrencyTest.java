package com.payflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.payflow.TestcontainersConfiguration;
import com.payflow.outbox.domain.OutboxEventEntity;
import com.payflow.outbox.publish.OutboxClaimRepository;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxClaimConcurrencyTest {

	@Autowired
	private OutboxClaimRepository claimRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void concurrentClaimersNeverShareTheSameEvent() throws Exception {
		UUID merchantId = seedMerchant();
		Set<UUID> seeded = new HashSet<>();
		Instant now = Instant.parse("2026-04-01T00:00:00Z");
		for (int i = 0; i < 20; i++) {
			UUID id = UUID.randomUUID();
			seeded.add(id);
			insertPending(id, merchantId, now);
		}

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<List<OutboxEventEntity>> a = pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return claimRepository.claimBatch(10, Duration.ofSeconds(30), Instant.now(), 8);
			});
			Future<List<OutboxEventEntity>> b = pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return claimRepository.claimBatch(10, Duration.ofSeconds(30), Instant.now(), 8);
			});
			start.countDown();

			List<OutboxEventEntity> first = a.get(15, TimeUnit.SECONDS);
			List<OutboxEventEntity> second = b.get(15, TimeUnit.SECONDS);

			Set<UUID> claimed = new HashSet<>();
			first.forEach(e -> claimed.add(e.getId()));
			second.forEach(e -> assertThat(claimed.add(e.getId())).isTrue());

			assertThat(first).isNotEmpty();
			assertThat(second).isNotEmpty();
			assertThat(claimed).hasSize(first.size() + second.size());
			assertThat(seeded).containsAll(claimed);

			for (OutboxEventEntity event : first) {
				assertThat(event.getClaimToken()).isNotNull();
				assertThat(event.getStatus().name()).isEqualTo("PROCESSING");
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void staleClaimTokenCannotMarkPublishedOrFailed() {
		UUID merchantId = seedMerchant();
		UUID eventId = UUID.randomUUID();
		insertPending(eventId, merchantId, Instant.now());

		List<OutboxEventEntity> claimed = claimRepository.claimBatch(1, Duration.ofSeconds(30), Instant.now(), 8);
		assertThat(claimed).hasSize(1);
		UUID realToken = claimed.getFirst().getClaimToken();
		UUID staleToken = UUID.randomUUID();

		assertThat(claimRepository.markPublished(eventId, staleToken, Instant.now())).isFalse();
		assertThat(claimRepository.markFailed(eventId, staleToken, "stale")).isFalse();

		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM outbox_events WHERE id = ?", String.class, eventId);
		assertThat(status).isEqualTo("PROCESSING");

		assertThat(claimRepository.markPublished(eventId, realToken, Instant.now())).isTrue();
		status = jdbcTemplate.queryForObject(
				"SELECT status FROM outbox_events WHERE id = ?", String.class, eventId);
		assertThat(status).isEqualTo("PUBLISHED");
		UUID token = jdbcTemplate.queryForObject(
				"SELECT claim_token FROM outbox_events WHERE id = ?", UUID.class, eventId);
		assertThat(token).isNull();
	}

	@Test
	void expiredProcessingLeaseIsReclaimable() {
		UUID merchantId = seedMerchant();
		UUID eventId = UUID.randomUUID();
		UUID oldToken = UUID.randomUUID();
		Instant past = Instant.now().minusSeconds(120);
		jdbcTemplate.update(
				"""
						INSERT INTO outbox_events (
						  id, aggregate_type, aggregate_id, event_type, event_schema_version,
						  merchant_id, payload, status, attempt_count, next_attempt_at,
						  created_at, claim_expires_at, claim_token)
						VALUES (?, 'PAYMENT', ?, 'payment.created', 1, ?, CAST('{}' AS jsonb),
						        'PROCESSING', 1, ?, ?, ?, ?)
						""",
				eventId, UUID.randomUUID(), merchantId,
				java.sql.Timestamp.from(past),
				java.sql.Timestamp.from(past),
				java.sql.Timestamp.from(past.plusSeconds(30)),
				oldToken);

		List<OutboxEventEntity> claimed = claimRepository.claimBatch(5, Duration.ofSeconds(30), Instant.now(), 8);
		assertThat(claimed).extracting(OutboxEventEntity::getId).contains(eventId);
		OutboxEventEntity reclaimed = claimed.stream()
				.filter(e -> e.getId().equals(eventId))
				.findFirst()
				.orElseThrow();
		assertThat(reclaimed.getClaimToken()).isNotEqualTo(oldToken);
		assertThat(reclaimed.getAttemptCount()).isEqualTo(2);
		assertThat(claimRepository.markPublished(eventId, oldToken, Instant.now())).isFalse();
		assertThat(claimRepository.markPublished(eventId, reclaimed.getClaimToken(), Instant.now())).isTrue();
	}

	private void insertPending(UUID id, UUID merchantId, Instant createdAt) {
		jdbcTemplate.update(
				"""
						INSERT INTO outbox_events (
						  id, aggregate_type, aggregate_id, event_type, event_schema_version,
						  merchant_id, payload, status, attempt_count, next_attempt_at, created_at)
						VALUES (?, 'PAYMENT', ?, 'payment.created', 1, ?, CAST(? AS jsonb),
						        'PENDING', 0, ?, ?)
						""",
				id, UUID.randomUUID(), merchantId, "{\"paymentId\":\"" + id + "\"}",
				java.sql.Timestamp.from(createdAt),
				java.sql.Timestamp.from(createdAt));
	}

	private UUID seedMerchant() {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		String email = "claim-" + userId + "@example.com";
		jdbcTemplate.update(
				"""
						INSERT INTO users (id, full_name, email, email_normalized, password_hash)
						VALUES (?, 'Claim', ?, ?, 'hash')
						""",
				userId, email, email);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Claim Merchant', 'ACTIVE', ?)
						""",
				merchantId, userId);
		return merchantId;
	}
}
