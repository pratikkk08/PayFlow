package com.payflow.webhooks.delivery;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.webhooks.domain.WebhookDeliveryEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Short-lived claim transactions using {@code FOR UPDATE SKIP LOCKED}.
 * Callers must commit before any outbound HTTP.
 */
@Repository
public class WebhookDeliveryClaimRepository {

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional
	public List<WebhookDeliveryEntity> claimBatch(int batchSize, Duration lease, Instant now, int maxAttempts) {
		Instant leaseExpiry = now.plus(lease);
		@SuppressWarnings("unchecked")
		List<UUID> ids = entityManager.createNativeQuery("""
				UPDATE webhook_deliveries AS d
				SET status = 'PROCESSING',
				    claim_token = gen_random_uuid(),
				    claim_expires_at = :leaseExpiry,
				    attempt_count = d.attempt_count + 1,
				    last_error = NULL
				FROM (
				    SELECT id
				    FROM webhook_deliveries
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
				WHERE d.id = claimable.id
				RETURNING d.id
				""")
				.setParameter("leaseExpiry", Timestamp.from(leaseExpiry))
				.setParameter("now", Timestamp.from(now))
				.setParameter("maxAttempts", maxAttempts)
				.setParameter("batchSize", batchSize)
				.getResultList()
				.stream()
				.map(WebhookDeliveryClaimRepository::toUuid)
				.toList();

		if (ids.isEmpty()) {
			return List.of();
		}

		List<WebhookDeliveryEntity> rows = entityManager
				.createQuery("select d from WebhookDeliveryEntity d where d.id in :ids", WebhookDeliveryEntity.class)
				.setParameter("ids", ids)
				.getResultList();
		List<WebhookDeliveryEntity> ordered = new ArrayList<>(rows.size());
		for (UUID id : ids) {
			rows.stream().filter(r -> r.getId().equals(id)).findFirst().ifPresent(ordered::add);
		}
		entityManager.clear();
		return ordered;
	}

	@Transactional
	public boolean markDelivered(UUID deliveryId, UUID claimToken, Instant deliveredAt, Integer httpStatus) {
		int updated = entityManager.createNativeQuery("""
				UPDATE webhook_deliveries
				SET status = 'DELIVERED',
				    delivered_at = :deliveredAt,
				    last_http_status = :httpStatus,
				    claim_expires_at = NULL,
				    claim_token = NULL,
				    last_error = NULL
				WHERE id = :deliveryId
				  AND claim_token = :claimToken
				  AND status = 'PROCESSING'
				""")
				.setParameter("deliveredAt", Timestamp.from(deliveredAt))
				.setParameter("httpStatus", httpStatus)
				.setParameter("deliveryId", deliveryId)
				.setParameter("claimToken", claimToken)
				.executeUpdate();
		return updated == 1;
	}

	@Transactional
	public boolean markRetry(UUID deliveryId, UUID claimToken, Instant nextAttemptAt, Integer httpStatus,
			String lastError) {
		int updated = entityManager.createNativeQuery("""
				UPDATE webhook_deliveries
				SET status = 'PENDING',
				    next_attempt_at = :nextAttemptAt,
				    last_http_status = :httpStatus,
				    claim_expires_at = NULL,
				    claim_token = NULL,
				    last_error = :lastError
				WHERE id = :deliveryId
				  AND claim_token = :claimToken
				  AND status = 'PROCESSING'
				""")
				.setParameter("nextAttemptAt", Timestamp.from(nextAttemptAt))
				.setParameter("httpStatus", httpStatus)
				.setParameter("lastError", truncate(lastError))
				.setParameter("deliveryId", deliveryId)
				.setParameter("claimToken", claimToken)
				.executeUpdate();
		return updated == 1;
	}

	@Transactional
	public boolean markFailed(UUID deliveryId, UUID claimToken, Integer httpStatus, String lastError) {
		int updated = entityManager.createNativeQuery("""
				UPDATE webhook_deliveries
				SET status = 'FAILED',
				    last_http_status = :httpStatus,
				    claim_expires_at = NULL,
				    claim_token = NULL,
				    last_error = :lastError
				WHERE id = :deliveryId
				  AND claim_token = :claimToken
				  AND status = 'PROCESSING'
				""")
				.setParameter("httpStatus", httpStatus)
				.setParameter("lastError", truncate(lastError))
				.setParameter("deliveryId", deliveryId)
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
			return "delivery_failed";
		}
		String sanitized = error.replaceAll("[\\r\\n\\t]+", " ").trim();
		return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
	}
}
