package com.payflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.payflow.TestcontainersConfiguration;
import com.payflow.outbox.publish.DomainEventEnvelope;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real-broker verification of the idempotent domain-event consumer (Testcontainers Kafka).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers
class DomainEventConsumerKafkaTest {

	private static final String TOPIC = "payflow.domain-events.v1";
	private static final String DLT = "payflow.domain-events.v1.dlt";
	private static final String IDENTITY = "payflow-domain-events-recorder";

	@Container
	static KafkaContainer kafka = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

	@DynamicPropertySource
	static void kafkaProps(DynamicPropertyRegistry registry) {
		registry.add("payflow.kafka.enabled", () -> "true");
		registry.add("payflow.kafka.consumer.enabled", () -> "true");
		registry.add("payflow.kafka.consumer.topic", () -> TOPIC);
		registry.add("payflow.kafka.consumer.dlt-topic", () -> DLT);
		registry.add("payflow.kafka.consumer.identity", () -> IDENTITY);
		registry.add("payflow.kafka.consumer.group-id", () -> "payflow-domain-events-it");
		registry.add("payflow.kafka.consumer.max-attempts", () -> "3");
		registry.add("payflow.kafka.consumer.backoff-interval-ms", () -> "200");
		registry.add("payflow.outbox.publisher.enabled", () -> "false");
		registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
		registry.add("spring.kafka.admin.fail-fast", () -> "false");
	}

	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private TestDomainEventProcessFaultInjector faultInjector;

	private KafkaTemplate<String, String> producer;

	@BeforeEach
	void setUp() {
		faultInjector.clear();
		producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.ACKS_CONFIG, "all")));
	}

	@Test
	void validEventConsumedOnceAndDuplicateRedeliveryDeduped() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String value = jsonMapper.writeValueAsString(sampleEnvelope(eventId, paymentId));

		producer.send(TOPIC, paymentId.toString(), value).get(10, TimeUnit.SECONDS);
		awaitProcessed(eventId, 1);

		// At-least-once redelivery of the same payload
		producer.send(TOPIC, paymentId.toString(), value).get(10, TimeUnit.SECONDS);
		Thread.sleep(1500);

		assertThat(countProcessed(eventId)).isEqualTo(1);
		assertThat(countForIdentity(eventId, IDENTITY)).isEqualTo(1);
		assertThat(countLedgerForAggregate(paymentId)).isZero();
	}

	@Test
	void invalidPayloadGoesToDltWithoutProcessedMarker() throws Exception {
		UUID eventId = UUID.randomUUID();
		String bad = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"payment.created\","
				+ "\"schemaVersion\":1,\"aggregateType\":\"PAYMENT\",\"aggregateId\":\""
				+ UUID.randomUUID() + "\",\"occurredAt\":\"2026-10-16T00:00:00Z\",\"payload\":{}}";

		try (KafkaConsumer<String, String> dltConsumer = newDltConsumer()) {
			dltConsumer.subscribe(List.of(DLT));
			dltConsumer.poll(Duration.ofMillis(500));

			producer.send(TOPIC, UUID.randomUUID().toString(), bad).get(10, TimeUnit.SECONDS);

			ConsumerRecord<String, String> dltRecord = awaitDlt(dltConsumer, eventId.toString());
			assertThat(dltRecord.value()).contains("payment.created");
		}

		assertThat(countProcessed(eventId)).isZero();
	}

	@Test
	void transientHandlerFailureRetriesThenSucceeds() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String value = jsonMapper.writeValueAsString(sampleEnvelope(eventId, paymentId));

		faultInjector.failNext(2);
		producer.send(TOPIC, paymentId.toString(), value).get(10, TimeUnit.SECONDS);

		awaitProcessed(eventId, 1);
		assertThat(countProcessed(eventId)).isEqualTo(1);
	}

	@Test
	void exhaustedRetriesGoToDltWithoutProcessedMarker() throws Exception {
		UUID eventId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String value = jsonMapper.writeValueAsString(sampleEnvelope(eventId, paymentId));

		faultInjector.failNext(20);

		try (KafkaConsumer<String, String> dltConsumer = newDltConsumer()) {
			dltConsumer.subscribe(List.of(DLT));
			dltConsumer.poll(Duration.ofMillis(500));

			producer.send(TOPIC, paymentId.toString(), value).get(10, TimeUnit.SECONDS);

			ConsumerRecord<String, String> dltRecord = awaitDlt(dltConsumer, eventId.toString());
			assertThat(dltRecord.value()).contains(eventId.toString());
		}

		assertThat(countProcessed(eventId)).isZero();
		faultInjector.clear();
	}

	private DomainEventEnvelope sampleEnvelope(UUID eventId, UUID paymentId) {
		UUID merchantId = UUID.randomUUID();
		JsonNode payload = jsonMapper.readTree("""
				{
				  "paymentId":"%s",
				  "merchantId":"%s",
				  "amountMinor":2200,
				  "currency":"INR",
				  "status":"CREATED",
				  "createdAt":"2026-10-16T00:00:00Z"
				}
				""".formatted(paymentId, merchantId));
		return new DomainEventEnvelope(
				eventId, "payment.created", 1, "PAYMENT", paymentId, merchantId,
				Instant.parse("2026-10-16T00:00:00Z"), payload);
	}

	private void awaitProcessed(UUID eventId, int expected) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 20_000;
		while (System.currentTimeMillis() < deadline) {
			if (countProcessed(eventId) >= expected) {
				return;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("Event " + eventId + " not processed; count=" + countProcessed(eventId));
	}

	private ConsumerRecord<String, String> awaitDlt(KafkaConsumer<String, String> consumer, String eventIdHint) {
		long deadline = System.currentTimeMillis() + 25_000;
		while (System.currentTimeMillis() < deadline) {
			ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
			for (ConsumerRecord<String, String> record : records) {
				if (record.value() != null && record.value().contains(eventIdHint)) {
					return record;
				}
			}
		}
		throw new AssertionError("No DLT record containing " + eventIdHint);
	}

	private KafkaConsumer<String, String> newDltConsumer() {
		return new KafkaConsumer<>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
				ConsumerConfig.GROUP_ID_CONFIG, "dlt-assert-" + UUID.randomUUID(),
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()));
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
