package com.payflow.outbox.consume;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
public class ProcessedEventEntity {

	@Id
	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Id
	@Column(name = "consumer_identity", nullable = false, length = 128)
	private String consumerIdentity;

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@Column(name = "schema_version", nullable = false)
	private int schemaVersion;

	@Column(name = "aggregate_type", nullable = false, length = 64)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected ProcessedEventEntity() {
	}

	public ProcessedEventEntity(UUID eventId, String consumerIdentity, String eventType, int schemaVersion,
			String aggregateType, UUID aggregateId, Instant processedAt) {
		this.eventId = eventId;
		this.consumerIdentity = consumerIdentity;
		this.eventType = eventType;
		this.schemaVersion = schemaVersion;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.processedAt = processedAt;
	}

	public UUID getEventId() {
		return eventId;
	}

	public String getConsumerIdentity() {
		return consumerIdentity;
	}

	public String getEventType() {
		return eventType;
	}

	public int getSchemaVersion() {
		return schemaVersion;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public UUID getAggregateId() {
		return aggregateId;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}
}
