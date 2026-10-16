package com.payflow.outbox.publish;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.outbox.domain.OutboxEventEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Short-lived claim transactions using {@code FOR UPDATE SKIP LOCKED}.
 * Callers must commit before any Kafka I/O.
 */
@Repository
public class OutboxClaimRepository {

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional
	public List<OutboxEventEntity> claimBatch(int batchSize, Duration lease, Instant now, int maxAttempts) {
		Instant leaseExpiry = now.plus(lease);
		@SuppressWarnings("unchecked")
		List<UUID> ids = entityManager.createNativeQuery("""
				UPDATE outbox_events AS o
				SET status = 'PROCESSING',
				    claim_token = gen_random_uuid(),
				    claim_expires_at = :leaseExpiry,
				    attempt_count = o.attempt_count + 1,
				    last_error = NULL
				FROM (
				    SELECT id
				    FROM outbox_events
				    WHERE (
				            (status = 'PENDING'
				                AND next_attempt_at <= :now
				                AND attempt_count < :maxAttempts)
				         OR (status = 'PROCESSING'
				                AND claim_expires_at < :now)
				        )
				    ORDER BY
				        CASE
				            WHEN status = 'PENDING' THEN next_attempt_at
				            ELSE claim_expires_at
				        END ASC,
				        id ASC
				    FOR UPDATE SKIP LOCKED
				    LIMIT :batchSize
				) AS claimable
				WHERE o.id = claimable.id
				RETURNING o.id
				""")
				.setParameter("leaseExpiry", Timestamp.from(leaseExpiry))
				.setParameter("now", Timestamp.from(now))
				.setParameter("maxAttempts", maxAttempts)
				.setParameter("batchSize", batchSize)
				.getResultList()
				.stream()
				.map(OutboxClaimRepository::toUuid)
				.toList();

		if (ids.isEmpty()) {
			return List.of();
		}

		List<OutboxEventEntity> events = entityManager
				.createQuery("select e from OutboxEventEntity e where e.id in :ids", OutboxEventEntity.class)
				.setParameter("ids", ids)
				.getResultList();
		// Preserve claim order
		List<OutboxEventEntity> ordered = new ArrayList<>(events.size());
		for (UUID id : ids) {
			events.stream().filter(e -> e.getId().equals(id)).findFirst().ifPresent(ordered::add);
		}
		entityManager.clear();
		return ordered;
	}

	@Transactional
	public boolean markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
		int updated = entityManager.createNativeQuery("""
				UPDATE outbox_events
				SET status = 'PUBLISHED',
				    published_at = :publishedAt,
				    claim_expires_at = NULL,
				    claim_token = NULL,
				    last_error = NULL
				WHERE id = :eventId
				  AND claim_token = :claimToken
				  AND status = 'PROCESSING'
				""")
				.setParameter("publishedAt", Timestamp.from(publishedAt))
				.setParameter("eventId", eventId)
				.setParameter("claimToken", claimToken)
				.executeUpdate();
		return updated == 1;
	}

	@Transactional
	public boolean markRetry(UUID eventId, UUID claimToken, Instant nextAttemptAt, String lastError) {
		int updated = entityManager.createNativeQuery("""
				UPDATE outbox_events
				SET status = 'PENDING',
				    next_attempt_at = :nextAttemptAt,
				    claim_expires_at = NULL,
				    claim_token = NULL,
				    last_error = :lastError
				WHERE id = :eventId
				  AND claim_token = :claimToken
				  AND status = 'PROCESSING'
				""")
				.setParameter("nextAttemptAt", Timestamp.from(nextAttemptAt))
				.setParameter("lastError", truncate(lastError))
				.setParameter("eventId", eventId)
				.setParameter("claimToken", claimToken)
				.executeUpdate();
		return updated == 1;
	}

	@Transactional
	public boolean markFailed(UUID eventId, UUID claimToken, String lastError) {
		int updated = entityManager.createNativeQuery("""
				UPDATE outbox_events
				SET status = 'FAILED',
				    claim_expires_at = NULL,
				    claim_token = NULL,
				    last_error = :lastError
				WHERE id = :eventId
				  AND claim_token = :claimToken
				  AND status = 'PROCESSING'
				""")
				.setParameter("lastError", truncate(lastError))
				.setParameter("eventId", eventId)
				.setParameter("claimToken", claimToken)
				.executeUpdate();
		return updated == 1;
	}

	private static UUID toUuid(Object value) {
		if (value instanceof UUID uuid) {
			return uuid;
		}
		return UUID.fromString(value.toString());
	}

	private static String truncate(String error) {
		if (error == null || error.isBlank()) {
			return "publish_failed";
		}
		String sanitized = error.replaceAll("[\\r\\n\\t]+", " ").trim();
		return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
	}
}