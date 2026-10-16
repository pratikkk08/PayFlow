package com.payflow.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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

import com.payflow.TestcontainersConfiguration;
import com.payflow.outbox.consume.DomainEventProcessingService;
import com.payflow.outbox.publish.DomainEventEnvelope;
import com.payflow.webhooks.delivery.WebhookDeliveryWorker;
import com.payflow.webhooks.delivery.WebhookSignatureService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, WebhookDeliveryTestConfig.class })
class WebhookDeliveryWorkerTest {

	@DynamicPropertySource
	static void deliveryProps(DynamicPropertyRegistry registry) {
		registry.add("payflow.webhooks.delivery.enabled", () -> "true");
		registry.add("payflow.webhooks.delivery.scheduling-enabled", () -> "false");
		registry.add("payflow.webhooks.delivery.max-attempts", () -> "3");
		registry.add("payflow.webhooks.delivery.backoff-initial-ms", () -> "10");
		registry.add("payflow.webhooks.delivery.backoff-max-ms", () -> "50");
		registry.add("payflow.webhooks.delivery.lease-seconds", () -> "30");
	}

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private DomainEventProcessingService processingService;
	@Autowired
	private WebhookDeliveryWorker deliveryWorker;
	@Autowired
	private TestWebhookHttpTransport webhookHttpTransport;
	@Autowired
	private WebhookDeliveryTestConfig.TestDnsResolver webhookDnsResolver;
	@Autowired
	private WebhookSignatureService signatureService;
	@Autowired
	private TestWebhookDeliveryEnqueueFaultInjector enqueueFaultInjector;

	@BeforeEach
	void reset() throws Exception {
		enqueueFaultInjector.clear();
		webhookHttpTransport.respondWithStatuses(200);
		webhookDnsResolver.setAddresses(InetAddress.getByName("8.8.8.8"));
	}

	@Test
	void eligibleEventCreatesOneDeliveryJobAndDuplicateDoesNot() throws Exception {
		String ownerToken = registerLogin("wd-enq-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		createWebhook(ownerToken, merchantId, "https://hooks.example.com/a",
				"[\"payment.created\",\"payment.captured\"]");

		UUID eventId = UUID.randomUUID();
		DomainEventEnvelope envelope = paymentEnvelope(eventId, UUID.fromString(merchantId), "payment.created");

		assertThat(processingService.processValidated(envelope)).isTrue();
		assertThat(countDeliveries(eventId)).isEqualTo(1);

		assertThat(processingService.processValidated(envelope)).isFalse();
		assertThat(countDeliveries(eventId)).isEqualTo(1);
		assertThat(countProcessed(eventId)).isEqualTo(1);
	}

	@Test
	void subscriptionAndMerchantFiltering() throws Exception {
		String ownerToken = registerLogin("wd-filt-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		createWebhook(ownerToken, merchantId, "https://hooks.example.com/captured-only",
				"[\"payment.captured\"]");

		UUID eventId = UUID.randomUUID();
		processingService.processValidated(
				paymentEnvelope(eventId, UUID.fromString(merchantId), "payment.created"));
		assertThat(countDeliveries(eventId)).isZero();

		UUID otherMerchantEvent = UUID.randomUUID();
		processingService.processValidated(
				paymentEnvelope(otherMerchantEvent, UUID.randomUUID(), "payment.captured"));
		assertThat(countDeliveries(otherMerchantEvent)).isZero();
	}

	@Test
	void successful2xxMarksDeliveredWithStableEventIdAndValidSignature() throws Exception {
		String ownerToken = registerLogin("wd-ok-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		MvcResult created = createWebhook(ownerToken, merchantId, "https://hooks.example.com/ok",
				"[\"payment.created\"]");
		String plaintextSecret = jsonMapper.readTree(created.getResponse().getContentAsString())
				.get("signingSecret").asText();

		UUID eventId = UUID.randomUUID();
		DomainEventEnvelope envelope = paymentEnvelope(eventId, UUID.fromString(merchantId), "payment.created");
		processingService.processValidated(envelope);

		int claimed = deliveryWorker.deliverOnce();
		assertThat(claimed).isGreaterThanOrEqualTo(1);
		assertThat(statusOf(eventId)).isEqualTo("DELIVERED");
		assertThat(webhookHttpTransport.calls()).hasSize(1);

		var call = webhookHttpTransport.calls().getFirst();
		assertThat(call.request().headers().get(WebhookSignatureService.HEADER_EVENT_ID))
				.isEqualTo(eventId.toString());
		long ts = Long.parseLong(call.request().headers().get(WebhookSignatureService.HEADER_TIMESTAMP));
		String sig = call.request().headers().get(WebhookSignatureService.HEADER_SIGNATURE);
		assertThat(signatureService.verify(plaintextSecret, ts, eventId.toString(), call.request().body(), sig))
				.isTrue();
		assertThat(call.destination().hostname()).isEqualTo("hooks.example.com");
		assertThat(call.destination().address().getHostAddress()).isEqualTo("8.8.8.8");
	}

	@Test
	void retryableStatusesThenSuccess() throws Exception {
		String ownerToken = registerLogin("wd-retry-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		createWebhook(ownerToken, merchantId, "https://hooks.example.com/retry", "[\"payment.created\"]");

		UUID eventId = UUID.randomUUID();
		processingService.processValidated(
				paymentEnvelope(eventId, UUID.fromString(merchantId), "payment.created"));

		webhookHttpTransport.respondWithStatuses(503);
		deliveryWorker.deliverOnce();
		assertThat(statusOf(eventId)).isEqualTo("PENDING");

		jdbcTemplate.update(
				"UPDATE webhook_deliveries SET next_attempt_at = NOW() - INTERVAL '1 second' WHERE event_id = ?",
				eventId);

		webhookHttpTransport.respondWithStatuses(200);
		deliveryWorker.deliverOnce();
		assertThat(statusOf(eventId)).isEqualTo("DELIVERED");
	}

	@Test
	void permanent4xxFailsWithoutEndlessRetries() throws Exception {
		String ownerToken = registerLogin("wd-4xx-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		createWebhook(ownerToken, merchantId, "https://hooks.example.com/gone", "[\"payment.created\"]");

		UUID eventId = UUID.randomUUID();
		processingService.processValidated(
				paymentEnvelope(eventId, UUID.fromString(merchantId), "payment.created"));

		webhookHttpTransport.respondWithStatuses(404);
		deliveryWorker.deliverOnce();
		assertThat(statusOf(eventId)).isEqualTo("FAILED");
		assertThat(webhookHttpTransport.calls()).hasSize(1);
	}

	@Test
	void disabledEndpointIsNotContacted() throws Exception {
		String ownerToken = registerLogin("wd-dis-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		MvcResult created = createWebhook(ownerToken, merchantId, "https://hooks.example.com/dis",
				"[\"payment.created\"]");
		String webhookId = jsonMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		UUID eventId = UUID.randomUUID();
		processingService.processValidated(
				paymentEnvelope(eventId, UUID.fromString(merchantId), "payment.created"));
		assertThat(countDeliveries(eventId)).isEqualTo(1);

		jdbcTemplate.update("UPDATE webhook_endpoints SET enabled = FALSE WHERE id = ?",
				UUID.fromString(webhookId));

		deliveryWorker.deliverOnce();
		assertThat(statusOf(eventId)).isEqualTo("FAILED");
		assertThat(webhookHttpTransport.calls()).isEmpty();
	}

	@Test
	void privateResolvedAddressRejectedWithoutHttp() throws Exception {
		String ownerToken = registerLogin("wd-ssrf-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		createWebhook(ownerToken, merchantId, "https://hooks.example.com/ssrf", "[\"payment.created\"]");

		UUID eventId = UUID.randomUUID();
		processingService.processValidated(
				paymentEnvelope(eventId, UUID.fromString(merchantId), "payment.created"));

		webhookDnsResolver.setAddresses(InetAddress.getByName("127.0.0.1"));
		deliveryWorker.deliverOnce();
		assertThat(statusOf(eventId)).isEqualTo("FAILED");
		assertThat(lastError(eventId)).contains("blocked");
		assertThat(webhookHttpTransport.calls()).isEmpty();
	}

	@Test
	void redirectRejectedAsPermanentFailure() throws Exception {
		String ownerToken = registerLogin("wd-redir-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		createWebhook(ownerToken, merchantId, "https://hooks.example.com/redir", "[\"payment.created\"]");

		UUID eventId = UUID.randomUUID();
		processingService.processValidated(
				paymentEnvelope(eventId, UUID.fromString(merchantId), "payment.created"));

		webhookHttpTransport.respondWithStatuses(302);
		deliveryWorker.deliverOnce();
		assertThat(statusOf(eventId)).isEqualTo("FAILED");
	}

	@Test
	void expiredLeaseIsRecoverableAndStaleClaimCannotFinalize() throws Exception {
		String ownerToken = registerLogin("wd-lease-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		createWebhook(ownerToken, merchantId, "https://hooks.example.com/lease", "[\"payment.created\"]");

		UUID eventId = UUID.randomUUID();
		processingService.processValidated(
				paymentEnvelope(eventId, UUID.fromString(merchantId), "payment.created"));

		UUID staleToken = UUID.randomUUID();
		jdbcTemplate.update("""
				UPDATE webhook_deliveries
				SET status = 'PROCESSING',
				    claim_token = ?,
				    claim_expires_at = NOW() - INTERVAL '1 minute',
				    attempt_count = 1
				WHERE event_id = ?
				""", staleToken, eventId);

		webhookHttpTransport.respondWithStatuses(200);
		int claimed = deliveryWorker.deliverOnce();
		assertThat(claimed).isGreaterThanOrEqualTo(1);
		assertThat(statusOf(eventId)).isEqualTo("DELIVERED");

		boolean staleFinalize = jdbcTemplate.update("""
				UPDATE webhook_deliveries
				SET status = 'FAILED', claim_token = NULL, claim_expires_at = NULL, last_error = 'stale'
				WHERE event_id = ? AND claim_token = ?
				""", eventId, staleToken) == 1;
		assertThat(staleFinalize).isFalse();
		assertThat(statusOf(eventId)).isEqualTo("DELIVERED");
	}

	@Test
	void forcedDbFailureRollsBackProcessedMarkerAndDeliveryJobs() throws Exception {
		String ownerToken = registerLogin("wd-fault-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		createWebhook(ownerToken, merchantId, "https://hooks.example.com/fault", "[\"payment.created\"]");

		UUID eventId = UUID.randomUUID();
		enqueueFaultInjector.failNextEnqueue();
		try {
			processingService.processValidated(
					paymentEnvelope(eventId, UUID.fromString(merchantId), "payment.created"));
		}
		catch (IllegalStateException ex) {
			assertThat(ex.getMessage()).contains("injected_webhook_enqueue_failure");
		}
		assertThat(countProcessed(eventId)).isZero();
		assertThat(countDeliveries(eventId)).isZero();
	}

	@Test
	void flywayWebhookDeliveriesMigrationApplied() {
		Integer table = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.tables
						WHERE table_name = 'webhook_deliveries'
						""",
				Integer.class);
		assertThat(table).isEqualTo(1);
		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '16' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	private DomainEventEnvelope paymentEnvelope(UUID eventId, UUID merchantId, String type) {
		UUID paymentId = UUID.randomUUID();
		JsonNode payload = jsonMapper.readTree("""
				{
				  "paymentId":"%s",
				  "merchantId":"%s",
				  "amountMinor":1000,
				  "currency":"INR",
				  "status":"CREATED",
				  "createdAt":"2026-10-16T00:00:00Z"
				}
				""".formatted(paymentId, merchantId));
		return new DomainEventEnvelope(eventId, type, 1, "PAYMENT", paymentId, merchantId,
				Instant.parse("2026-10-16T00:00:00Z"), payload);
	}

	private MvcResult createWebhook(String token, String merchantId, String url, String eventTypesJson)
			throws Exception {
		return mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"url":"%s","eventTypes":%s,"enabled":true}
						""".formatted(url, eventTypesJson)))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private String createMerchant(String token) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Webhook Delivery Merchant"}
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

	private long countDeliveries(UUID eventId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM webhook_deliveries WHERE event_id = ?", Long.class, eventId);
		return count == null ? 0 : count;
	}

	private long countProcessed(UUID eventId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Long.class, eventId);
		return count == null ? 0 : count;
	}

	private String statusOf(UUID eventId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM webhook_deliveries WHERE event_id = ?", String.class, eventId);
	}

	private String lastError(UUID eventId) {
		return jdbcTemplate.queryForObject(
				"SELECT last_error FROM webhook_deliveries WHERE event_id = ?", String.class, eventId);
	}
}
