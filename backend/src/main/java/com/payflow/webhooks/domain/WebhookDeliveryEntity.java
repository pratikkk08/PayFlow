package com.payflow.webhooks.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDeliveryEntity {

	@Id
	private UUID id;

	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(name = "endpoint_id", nullable = false)
	private UUID endpointId;

	@Column(name = "merchant_id", nullable = false)
	private UUID merchantId;

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private WebhookDeliveryStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "claim_token")
	private UUID claimToken;

	@Column(name = "claim_expires_at")
	private Instant claimExpiresAt;

	@Column(name = "last_http_status")
	private Integer lastHttpStatus;

	@Column(name = "last_error", length = 512)
	private String lastError;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	protected WebhookDeliveryEntity() {
	}

	public WebhookDeliveryEntity(UUID id, UUID eventId, UUID endpointId, UUID merchantId, String eventType,
			String payloadJson, Instant now) {
		this.id = id;
		this.eventId = eventId;
		this.endpointId = endpointId;
		this.merchantId = merchantId;
		this.eventType = eventType;
		this.payload = payloadJson;
		this.status = WebhookDeliveryStatus.PENDING;
		this.attemptCount = 0;
		this.nextAttemptAt = now;
		this.createdAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getEndpointId() {
		return endpointId;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public WebhookDeliveryStatus getStatus() {
		return status;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public UUID getClaimToken() {
		return claimToken;
	}

	public Instant getClaimExpiresAt() {
		return claimExpiresAt;
	}

	public Integer getLastHttpStatus() {
		return lastHttpStatus;
	}

	public String getLastError() {
		return lastError;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getDeliveredAt() {
		return deliveredAt;
	}
}
