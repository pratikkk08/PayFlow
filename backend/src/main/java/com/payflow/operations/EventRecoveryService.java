package com.payflow.operations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.identity.service.SecurityAuditService;

/**
 * Requeues terminal FAILED outbox events and webhook deliveries.
 * Conditional updates keep claim tokens and in-flight rows untouched.
 * No HTTP mutation surface until trusted ADMIN provisioning exists.
 */
@Service
public class EventRecoveryService {

	private static final Logger log = LoggerFactory.getLogger(EventRecoveryService.class);

	private static final String REQUEUE_OUTBOX = """
			UPDATE outbox_events
			SET status = 'PENDING',
			    attempt_count = 0,
			    next_attempt_at = ?,
			    last_error = NULL,
			    claim_token = NULL,
			    claim_expires_at = NULL
			WHERE id = ?
			  AND status = 'FAILED'
			  AND published_at IS NULL
			  AND claim_token IS NULL
			  AND claim_expires_at IS NULL
			""";

	private static final String REQUEUE_DELIVERY = """
			UPDATE webhook_deliveries
			SET status = 'PENDING',
			    attempt_count = 0,
			    next_attempt_at = ?,
			    last_error = NULL,
			    last_http_status = NULL,
			    claim_token = NULL,
			    claim_expires_at = NULL
			WHERE id = ?
			  AND status = 'FAILED'
			  AND delivered_at IS NULL
			  AND claim_token IS NULL
			  AND claim_expires_at IS NULL
			""";

	private final JdbcTemplate jdbcTemplate;
	private final SecurityAuditService securityAuditService;

	public EventRecoveryService(JdbcTemplate jdbcTemplate, SecurityAuditService securityAuditService) {
		this.jdbcTemplate = jdbcTemplate;
		this.securityAuditService = securityAuditService;
	}

	/**
	 * @return true when this call moved the row FAILED → PENDING
	 */
	@Transactional
	public boolean requeueFailedOutboxEvent(UUID eventId, UUID actorUserId) {
		int updated = jdbcTemplate.update(REQUEUE_OUTBOX, Timestamp.from(Instant.now()), eventId);
		if (updated != 1) {
			log.info("outbox_requeue outcome=rejected eventId={} actorUserId={}", eventId, actorUserId);
			return false;
		}
		securityAuditService.record("outbox_event_requeued", actorUserId, null, null,
				"eventId=" + eventId, null);
		log.info("outbox_requeue outcome=requeued eventId={} actorUserId={}", eventId, actorUserId);
		return true;
	}

	/**
	 * @return true when this call moved the row FAILED → PENDING
	 */
	@Transactional
	public boolean requeueFailedWebhookDelivery(UUID deliveryId, UUID actorUserId) {
		int updated = jdbcTemplate.update(REQUEUE_DELIVERY, Timestamp.from(Instant.now()), deliveryId);
		if (updated != 1) {
			log.info("webhook_requeue outcome=rejected deliveryId={} actorUserId={}", deliveryId, actorUserId);
			return false;
		}
		securityAuditService.record("webhook_delivery_requeued", actorUserId, null, null,
				"deliveryId=" + deliveryId, null);
		log.info("webhook_requeue outcome=requeued deliveryId={} actorUserId={}", deliveryId, actorUserId);
		return true;
	}
}
