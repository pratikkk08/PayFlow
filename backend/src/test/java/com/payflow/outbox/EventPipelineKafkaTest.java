package com.payflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
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

import tools.jackson.databind.json.JsonMapper;

/**
 * Payment transition → outbox → Kafka → consumer → one webhook delivery job.
 * HTTP delivery is intentionally not part of this test. Delivery is at-least-once:
 * a redelivered Kafka record must not create a second job or a second ledger posting.
 * Receivers must still dedupe by event id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers
class EventPipelineKafkaTest {

	private static final String TOPIC = "payflow.domain-events.v1";

	@Container
	static KafkaContainer kafka = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

	@DynamicPropertySource
	static void kafkaProps(DynamicPropertyRegistry registry) {
		registry.add("payflow.kafka.enabled", () -> "true");
		registry.add("payflow.kafka.consumer.enabled", () -> "true");
		registry.add("payflow.kafka.consumer.topic", () -> TOPIC);
		registry.add("payflow.kafka.consumer.group-id", () -> "payflow-pipeline-it");
		registry.add("payflow.outbox.publisher.enabled", () -> "true");
		registry.add("payflow.outbox.publisher.scheduling-enabled", () -> "false");
		registry.add("payflow.outbox.publisher.topic", () -> TOPIC);
		registry.add("payflow.webhooks.delivery.enabled", () -> "false");
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
	void capturePublishesOnceAndConsumerEnqueuesOneDeliveryJob() throws Exception {
		String ownerToken = registerLogin("pipe-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		createWebhook(ownerToken, merchantId);
		String paymentId = createPayment(ownerToken, merchantId, "pipe-ord", 3300);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());

		UUID paymentUuid = UUID.fromString(paymentId);
		assertThat(eventStatus("payment.captured", paymentUuid)).isEqualTo("PENDING");
		assertThat(countDeliveries(paymentUuid, "payment.captured")).isZero();
		assertThat(countLedger(paymentUuid)).isEqualTo(1);

		publishUntil(paymentUuid, "payment.captured");
		awaitDeliveries(paymentUuid, "payment.captured", 1);

		UUID eventId = jdbcTemplate.queryForObject(
				"""
						SELECT id FROM outbox_events
						WHERE aggregate_id = ? AND event_type = 'payment.captured'
						""",
				UUID.class, paymentUuid);
		assertThat(eventStatus("payment.captured", paymentUuid)).isEqualTo("PUBLISHED");
		assertThat(countProcessed(eventId)).isEqualTo(1);
		assertThat(countDeliveries(paymentUuid, "payment.captured")).isEqualTo(1);
		assertThat(countLedger(paymentUuid)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, paymentUuid)).isEqualTo("CAPTURED");

		String body = jdbcTemplate.queryForObject(
				"SELECT payload::text FROM webhook_deliveries WHERE event_id = ?",
				String.class, eventId);
		assertThat(body).contains(eventId.toString()).doesNotContain("whsec_");

		KafkaTemplate<String, String> producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
				ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.ACKS_CONFIG, "all")));
		producer.send(TOPIC, paymentId, body).get(10, TimeUnit.SECONDS);
		Thread.sleep(1500);

		assertThat(countProcessed(eventId)).isEqualTo(1);
		assertThat(countDeliveries(paymentUuid, "payment.captured")).isEqualTo(1);
		assertThat(countLedger(paymentUuid)).isEqualTo(1);
	}

	private void publishUntil(UUID paymentId, String eventType) {
		long deadline = System.currentTimeMillis() + 20_000;
		while (System.currentTimeMillis() < deadline) {
			if ("PUBLISHED".equals(eventStatus(eventType, paymentId))) {
				return;
			}
			outboxPublisher.publishOnce();
		}
		throw new AssertionError("Outbox event " + eventType + " was not published");
	}

	private void awaitDeliveries(UUID paymentId, String eventType, int expected) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 20_000;
		while (System.currentTimeMillis() < deadline) {
			if (countDeliveries(paymentId, eventType) >= expected) {
				return;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("Expected " + expected + " deliveries for " + eventType
				+ " but found " + countDeliveries(paymentId, eventType));
	}

	private String eventStatus(String eventType, UUID aggregateId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM outbox_events WHERE event_type = ? AND aggregate_id = ?",
				String.class, eventType, aggregateId);
	}

	private int countDeliveries(UUID paymentId, String eventType) {
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM webhook_deliveries d
						JOIN outbox_events e ON e.id = d.event_id
						WHERE e.aggregate_id = ? AND e.event_type = ?
						""",
				Integer.class, paymentId, eventType);
		return count == null ? 0 : count;
	}

	private int countProcessed(UUID eventId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
				Integer.class, eventId);
		return count == null ? 0 : count;
	}

	private int countLedger(UUID paymentId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ledger_transactions WHERE source_id = ?",
				Integer.class, paymentId);
		return count == null ? 0 : count;
	}

	private void createWebhook(String token, String merchantId) throws Exception {
		mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"url":"https://hooks.example.com/pipeline","eventTypes":["payment.captured"],"enabled":true}
						"""))
				.andExpect(status().isCreated());
	}

	private String createPayment(String token, String merchantId, String reference, long amount) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + token)
				.header("Idempotency-Key", "pipe-" + UUID.randomUUID())
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
						{"name":"Pipeline Merchant"}
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
