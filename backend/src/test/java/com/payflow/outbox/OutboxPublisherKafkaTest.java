package com.payflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.payflow.TestcontainersConfiguration;
import com.payflow.outbox.publish.OutboxPublisher;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real-broker verification via Testcontainers Kafka (not the Compose profile).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers
class OutboxPublisherKafkaTest {

	private static final String TOPIC = "payflow.domain-events.v1";

	@Container
	static KafkaContainer kafka = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

	@DynamicPropertySource
	static void kafkaProps(DynamicPropertyRegistry registry) {
		registry.add("payflow.kafka.enabled", () -> "true");
		registry.add("payflow.outbox.publisher.enabled", () -> "true");
		registry.add("payflow.outbox.publisher.scheduling-enabled", () -> "false");
		registry.add("payflow.outbox.publisher.topic", () -> TOPIC);
		registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
		registry.add("spring.kafka.admin.fail-fast", () -> "false");
	}

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private OutboxPublisher outboxPublisher;

	@Test
	void pendingEventPublishesAfterBrokerAckWithCorrectEnvelopeAndKey() throws Exception {
		String ownerToken = registerLogin("pub-kaf-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId, "pub-kaf-1", 2500);

		assertThat(eventStatus("payment.created", paymentId)).isEqualTo("PENDING");
		String paymentStatusBefore = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId));

		try (KafkaConsumer<String, String> consumer = newConsumer("pub-kaf-" + UUID.randomUUID())) {
			consumer.subscribe(List.of(TOPIC));
			// Warm partition assignment
			consumer.poll(Duration.ofMillis(500));

			int claimed = outboxPublisher.publishOnce();
			assertThat(claimed).isGreaterThanOrEqualTo(1);

			ConsumerRecord<String, String> record = awaitRecord(consumer, paymentId);
			assertThat(record.key()).isEqualTo(paymentId);

			JsonNode envelope = jsonMapper.readTree(record.value());
			assertThat(envelope.get("eventType").asText()).isEqualTo("payment.created");
			assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
			assertThat(envelope.get("aggregateType").asText()).isEqualTo("PAYMENT");
			assertThat(envelope.get("aggregateId").asText()).isEqualTo(paymentId);
			assertThat(envelope.get("merchantId").asText()).isEqualTo(merchantId);
			assertThat(envelope.get("eventId").asText()).isNotBlank();
			assertThat(envelope.get("payload").get("paymentId").asText()).isEqualTo(paymentId);
			assertThat(envelope.get("payload").has("password")).isFalse();
			assertThat(new String(record.headers().lastHeader("eventId").value()))
					.isEqualTo(envelope.get("eventId").asText());
		}

		assertThat(eventStatus("payment.created", paymentId)).isEqualTo("PUBLISHED");
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"""
						SELECT published_at, claim_token, claim_expires_at, status
						FROM outbox_events
						WHERE event_type = 'payment.created' AND aggregate_id = ?
						""",
				UUID.fromString(paymentId));
		assertThat(row.get("published_at")).isNotNull();
		assertThat(row.get("claim_token")).isNull();
		assertThat(row.get("claim_expires_at")).isNull();
		assertThat(row.get("status")).isEqualTo("PUBLISHED");

		String paymentStatusAfter = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId));
		assertThat(paymentStatusAfter).isEqualTo(paymentStatusBefore).isEqualTo("CREATED");
		Integer ledger = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ledger_transactions WHERE source_id = ?",
				Integer.class, UUID.fromString(paymentId));
		assertThat(ledger).isZero();
	}

	private ConsumerRecord<String, String> awaitRecord(KafkaConsumer<String, String> consumer, String paymentId) {
		long deadline = System.currentTimeMillis() + 20_000;
		while (System.currentTimeMillis() < deadline) {
			ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
			for (ConsumerRecord<String, String> record : records) {
				if (paymentId.equals(record.key()) && record.value().contains("payment.created")) {
					return record;
				}
			}
		}
		throw new AssertionError("No Kafka record for payment " + paymentId);
	}

	private KafkaConsumer<String, String> newConsumer(String groupId) {
		return new KafkaConsumer<>(Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
				ConsumerConfig.GROUP_ID_CONFIG, groupId,
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()));
	}

	private String eventStatus(String eventType, String aggregateId) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT status FROM outbox_events
						WHERE event_type = ? AND aggregate_id = ?
						""",
				String.class, eventType, UUID.fromString(aggregateId));
	}

	private String createPayment(String token, String merchantId, String reference, long amount) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + token)
				.header("Idempotency-Key", "pub-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"%s","amountMinor":%d,"currency":"INR"}
						""".formatted(merchantId, reference, amount)))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonMapper.readTree(created.getResponse().getContentAsString()).get("paymentId").asText();
	}

	private String createMerchant(String token) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Publisher Kafka Merchant"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
	}

	private String registerLogin(String email) throws Exception {
		String password = "Str0ngPass!";
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fullName":"User","email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		return jsonMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
	}
}
