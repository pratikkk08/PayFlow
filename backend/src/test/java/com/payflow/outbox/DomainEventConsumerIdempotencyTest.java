package com.payflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.payflow.TestcontainersConfiguration;
import com.payflow.outbox.consume.DomainEventProcessingService;
import com.payflow.outbox.consume.DomainEventValidator;
import com.payflow.outbox.consume.InvalidDomainEventException;
import com.payflow.outbox.publish.DomainEventEnvelope;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DomainEventConsumerIdempotencyTest {

	@Autowired
	private DomainEventValidator validator;
	@Autowired
	private DomainEventProcessingService processingService;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private TestDomainEventProcessFaultInjector faultInjector;

	@BeforeEach
	void clearFaults() {
		faultInjector.clear();
	}

	@Test
	void validEventIsProcessedAndRecordedOnce() {
		DomainEventEnvelope envelope = samplePaymentEnvelope(UUID.randomUUID());

		boolean first = processingService.processValidated(envelope);
		boolean second = processingService.processValidated(envelope);

		assertThat(first).isTrue();
		assertThat(second).isFalse();
		assertThat(countProcessed(envelope.eventId())).isEqualTo(1);
		assertThat(countLedgerForAggregate(envelope.aggregateId())).isZero();
	}

	@Test
	void redeliveryAfterSimulatedRestartRemainsDeduplicated() {
		DomainEventEnvelope envelope = samplePaymentEnvelope(UUID.randomUUID());
		assertThat(processingService.processValidated(envelope)).isTrue();

		// Simulate process restart: new call with the same event id.
		assertThat(processingService.processValidated(envelope)).isFalse();
		assertThat(countProcessed(envelope.eventId())).isEqualTo(1);
	}

	@Test
	void sameEventIdProcessedIndependentlyByDistinctConsumerIdentities() {
		DomainEventEnvelope envelope = samplePaymentEnvelope(UUID.randomUUID());

		assertThat(processingService.processValidatedForIdentity(envelope, "consumer-a")).isTrue();
		assertThat(processingService.processValidatedForIdentity(envelope, "consumer-b")).isTrue();
		assertThat(processingService.processValidatedForIdentity(envelope, "consumer-a")).isFalse();

		assertThat(countProcessed(envelope.eventId())).isEqualTo(2);
		assertThat(countForIdentity(envelope.eventId(), "consumer-a")).isEqualTo(1);
		assertThat(countForIdentity(envelope.eventId(), "consumer-b")).isEqualTo(1);
	}

	@Test
	void malformedAndUnsupportedEventsRejectedWithoutProcessedMarker() {
		UUID eventId = UUID.randomUUID();

		assertThatThrownBy(() -> validator.parseAndValidate("{not-json"))
				.isInstanceOf(InvalidDomainEventException.class);

		assertThatThrownBy(() -> validator.parseAndValidate(jsonMapper.writeValueAsString(unsupportedType())))
				.isInstanceOf(InvalidDomainEventException.class);

		String unsupportedSchema = envelopeJson(eventId, "payment.created", 99, "PAYMENT",
				UUID.randomUUID(), validPaymentPayload(UUID.randomUUID()));
		assertThatThrownBy(() -> validator.parseAndValidate(unsupportedSchema))
				.isInstanceOf(InvalidDomainEventException.class)
				.hasMessageContaining("unsupported_schema_version");

		String missingField = envelopeJson(eventId, "payment.created", 1, "PAYMENT",
				UUID.randomUUID(), jsonMapper.readTree("{\"paymentId\":\"" + UUID.randomUUID() + "\"}"));
		assertThatThrownBy(() -> validator.parseAndValidate(missingField))
				.isInstanceOf(InvalidDomainEventException.class)
				.hasMessageContaining("missing_payload_field");

		assertThat(countProcessed(eventId)).isZero();
	}

	@Test
	void handlerDatabaseFailureDoesNotLeaveProcessedMarker() {
		DomainEventEnvelope envelope = samplePaymentEnvelope(UUID.randomUUID());
		faultInjector.failNext(1);

		assertThatThrownBy(() -> processingService.processValidated(envelope))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("injected_domain_event_process_failure");

		assertThat(countProcessed(envelope.eventId())).isZero();

		faultInjector.clear();
		assertThat(processingService.processValidated(envelope)).isTrue();
		assertThat(countProcessed(envelope.eventId())).isEqualTo(1);
	}

	@Test
	void flywayProcessedEventsTableExists() {
		Integer tables = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.tables
						WHERE table_schema = 'public' AND table_name = 'processed_events'
						""",
				Integer.class);
		assertThat(tables).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '14' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	private DomainEventEnvelope unsupportedType() {
		return new DomainEventEnvelope(
				UUID.randomUUID(),
				"payment.unknown",
				1,
				"PAYMENT",
				UUID.randomUUID(),
				UUID.randomUUID(),
				Instant.parse("2026-10-16T00:00:00Z"),
				validPaymentPayload(UUID.randomUUID()));
	}

	private DomainEventEnvelope samplePaymentEnvelope(UUID eventId) {
		UUID paymentId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		return new DomainEventEnvelope(
				eventId,
				"payment.created",
				1,
				"PAYMENT",
				paymentId,
				merchantId,
				Instant.parse("2026-10-16T00:00:00Z"),
				validPaymentPayload(paymentId, merchantId));
	}

	private JsonNode validPaymentPayload(UUID paymentId) {
		return validPaymentPayload(paymentId, UUID.randomUUID());
	}

	private JsonNode validPaymentPayload(UUID paymentId, UUID merchantId) {
		return jsonMapper.readTree("""
				{
				  "paymentId":"%s",
				  "merchantId":"%s",
				  "amountMinor":1500,
				  "currency":"INR",
				  "status":"CREATED",
				  "createdAt":"2026-10-16T00:00:00Z"
				}
				""".formatted(paymentId, merchantId));
	}

	private String envelopeJson(UUID eventId, String type, int schemaVersion, String aggregateType,
			UUID aggregateId, JsonNode payload) {
		return jsonMapper.writeValueAsString(new DomainEventEnvelope(
				eventId, type, schemaVersion, aggregateType, aggregateId, UUID.randomUUID(),
				Instant.parse("2026-10-16T00:00:00Z"), payload));
	}

	private long countProcessed(UUID eventId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Long.class, eventId);
		return count == null ? 0 : count;
	}

	private long countForIdentity(UUID eventId, String identity) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM processed_events WHERE event_id = ? AND consumer_identity = ?",
				Long.class, eventId, identity);
		return count == null ? 0 : count;
	}

	private long countLedgerForAggregate(UUID aggregateId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ledger_transactions WHERE source_id = ?", Long.class, aggregateId);
		return count == null ? 0 : count;
	}
}
