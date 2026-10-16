package com.payflow.outbox.consume;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.payflow.outbox.domain.OutboxAggregateType;
import com.payflow.outbox.domain.OutboxEventType;
import com.payflow.outbox.publish.DomainEventEnvelope;
import com.payflow.outbox.service.OutboxEventWriter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses and validates {@link DomainEventEnvelope} JSON from Kafka.
 * Invalid / unsupported envelopes throw {@link InvalidDomainEventException} (non-retryable → DLT).
 */
@Component
public class DomainEventValidator {

	private static final Set<String> PAYMENT_REQUIRED = Set.of(
			"paymentId", "merchantId", "amountMinor", "currency", "status", "createdAt");
	private static final Set<String> REFUND_REQUIRED = Set.of(
			"refundId", "paymentId", "merchantId", "amountMinor", "currency", "status", "createdAt");

	private final JsonMapper jsonMapper;

	public DomainEventValidator(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public DomainEventEnvelope parseAndValidate(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) {
			throw new InvalidDomainEventException("empty_event_payload");
		}

		DomainEventEnvelope envelope;
		try {
			envelope = jsonMapper.readValue(rawValue, DomainEventEnvelope.class);
		}
		catch (RuntimeException ex) {
			throw new InvalidDomainEventException("malformed_event_json", ex);
		}

		if (envelope == null) {
			throw new InvalidDomainEventException("null_event_envelope");
		}
		if (envelope.eventId() == null) {
			throw new InvalidDomainEventException("missing_event_id");
		}
		if (envelope.eventType() == null || envelope.eventType().isBlank()) {
			throw new InvalidDomainEventException("missing_event_type");
		}

		OutboxEventType eventType = OutboxEventType.fromWireValue(envelope.eventType())
				.orElseThrow(() -> new InvalidDomainEventException(
						"unsupported_event_type:" + envelope.eventType()));

		if (envelope.schemaVersion() != OutboxEventWriter.EVENT_SCHEMA_VERSION) {
			throw new InvalidDomainEventException(
					"unsupported_schema_version:" + envelope.schemaVersion());
		}
		if (envelope.aggregateType() == null || envelope.aggregateType().isBlank()) {
			throw new InvalidDomainEventException("missing_aggregate_type");
		}
		try {
			OutboxAggregateType.valueOf(envelope.aggregateType());
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidDomainEventException("unsupported_aggregate_type:" + envelope.aggregateType());
		}
		if (envelope.aggregateId() == null) {
			throw new InvalidDomainEventException("missing_aggregate_id");
		}
		if (envelope.occurredAt() == null) {
			throw new InvalidDomainEventException("missing_occurred_at");
		}
		if (envelope.payload() == null || !envelope.payload().isObject()) {
			throw new InvalidDomainEventException("invalid_payload_structure");
		}

		validatePayloadShape(eventType, envelope.payload());
		validateAggregateConsistency(eventType, envelope);
		return envelope;
	}

	private void validatePayloadShape(OutboxEventType eventType, JsonNode payload) {
		Set<String> required = eventType.isRefundEvent() ? REFUND_REQUIRED : PAYMENT_REQUIRED;
		for (String field : required) {
			if (!payload.has(field) || payload.get(field).isNull()) {
				throw new InvalidDomainEventException("missing_payload_field:" + field);
			}
		}
		requireUuid(payload, "paymentId");
		requireUuid(payload, "merchantId");
		if (eventType.isRefundEvent()) {
			requireUuid(payload, "refundId");
		}
		if (!payload.get("amountMinor").isNumber() || payload.get("amountMinor").asLong() <= 0) {
			throw new InvalidDomainEventException("invalid_payload_field:amountMinor");
		}
		if (!payload.get("currency").isString() || payload.get("currency").asText().isBlank()) {
			throw new InvalidDomainEventException("invalid_payload_field:currency");
		}
		if (!payload.get("status").isString() || payload.get("status").asText().isBlank()) {
			throw new InvalidDomainEventException("invalid_payload_field:status");
		}
	}

	private void validateAggregateConsistency(OutboxEventType eventType, DomainEventEnvelope envelope) {
		if (eventType.isPaymentEvent() && !"PAYMENT".equals(envelope.aggregateType())) {
			throw new InvalidDomainEventException("aggregate_type_mismatch");
		}
		if (eventType.isRefundEvent() && !"REFUND".equals(envelope.aggregateType())) {
			throw new InvalidDomainEventException("aggregate_type_mismatch");
		}
	}

	private static void requireUuid(JsonNode payload, String field) {
		try {
			UUID.fromString(payload.get(field).asText());
		}
		catch (RuntimeException ex) {
			throw new InvalidDomainEventException("invalid_payload_field:" + field, ex);
		}
	}
}
