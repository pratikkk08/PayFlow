package com.payflow.outbox.publish;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * Kafka value envelope for {@code payflow.domain-events.v1}.
 * Record key is {@code aggregateId} (string form).
 */
public record DomainEventEnvelope(
		UUID eventId,
		String eventType,
		int schemaVersion,
		String aggregateType,
		UUID aggregateId,
		UUID merchantId,
		Instant occurredAt,
		JsonNode payload) {
}
