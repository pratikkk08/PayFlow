package com.payflow.outbox.domain;

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
@Table(name = "outbox_events")
public class OutboxEventEntity {

	@Id
	private UUID id;

	@Column(name = "aggregate_type", nullable = false, length = 64)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@Column(name = "event_schema_version", nullable = false)
	private int eventSchemaVersion;

	@Column(name = "merchant_id")
	private UUID merchantId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OutboxEventStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "last_error", length = 512)
	private String lastError;

	@Column(name = "claim_expires_at")
	private Instant claimExpiresAt;

	@Column(name = "claim_token")
	private UUID claimToken;

	protected OutboxEventEntity() {
	}

	public OutboxEventEntity(UUID id, String aggregateType, UUID aggregateId, String eventType,
			int eventSchemaVersion, UUID merchantId, String payload, Instant createdAt) {
		this.id = id;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.eventSchemaVersion = eventSchemaVersion;
		this.merchantId = merchantId;
		this.payload = payload;
		this.status = OutboxEventStatus.PENDING;
		this.attemptCount = 0;
		this.nextAttemptAt = createdAt;
		this.createdAt = createdAt;
	}

	public static OutboxEventEntity pending(UUID id, OutboxAggregateType aggregateType, UUID aggregateId,
			OutboxEventType eventType, int eventSchemaVersion, UUID merchantId, String payloadJson,
			Instant createdAt) {
		return new OutboxEventEntity(id, aggregateType.name(), aggregateId, eventType.wireValue(),
				eventSchemaVersion, merchantId, payloadJson, createdAt);
	}

	public UUID getId() {
		return id;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public UUID getAggregateId() {
		return aggregateId;
	}

	public String getEventType() {
		return eventType;
	}

	public int getEventSchemaVersion() {
		return eventSchemaVersion;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public String getPayload() {
		return payload;
	}

	public OutboxEventStatus getStatus() {
		return status;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public String getLastError() {
		return lastError;
	}

	public Instant getClaimExpiresAt() {
		return claimExpiresAt;
	}

	public UUID getClaimToken() {
		return claimToken;
	}
}
