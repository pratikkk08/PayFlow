package com.payflow.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.payflow.TestcontainersConfiguration;
import com.payflow.outbox.domain.OutboxEventEntity;
import com.payflow.outbox.publish.OutboxClaimRepository;
import com.payflow.shared.security.JwtTokenService;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EventOperationsTest {

	private static final String PAYLOAD_MARKER = "OPS_PAYLOAD_MARKER";
	private static final String URL_MARKER = "https://hooks.example.com/ops-secret-path";
	private static final String SECRET_MARKER = "whsec_SHOULD_NOT_LEAK";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private JwtTokenService jwtTokenService;
	@Autowired
	private EventOperationsService operationsService;
	@Autowired
	private EventOperationsSnapshotRepository snapshotRepository;
	@Autowired
	private EventRecoveryService recoveryService;
	@Autowired
	private EventPipelineMetrics metrics;
	@Autowired
	private MeterRegistry meterRegistry;
	@Autowired
	private OutboxClaimRepository claimRepository;

	@Test
	void operationalGaugesMatchAggregateCounts() {
		EventOperationsSnapshot before = snapshotRepository.load();
		UUID merchantId = seedMerchant();
		UUID endpointId = seedEndpoint(merchantId);
		insertOutbox(UUID.randomUUID(), merchantId, "PENDING", 0, null, null, Instant.now());
		insertOutbox(UUID.randomUUID(), merchantId, "FAILED", 8, null, null, Instant.now());
		insertOutbox(UUID.randomUUID(), merchantId, "PROCESSING", 1, UUID.randomUUID(),
				Instant.now().minusSeconds(90), Instant.now().minusSeconds(120));
		insertDelivery(UUID.randomUUID(), UUID.randomUUID(), endpointId, merchantId, "PENDING", 0, null, null);
		insertDelivery(UUID.randomUUID(), UUID.randomUUID(), endpointId, merchantId, "FAILED", 8, null, null);
		insertDelivery(UUID.randomUUID(), UUID.randomUUID(), endpointId, merchantId, "PROCESSING", 1,
				UUID.randomUUID(), Instant.now().minusSeconds(90));

		EventOperationsSnapshot after = operationsService.refresh();
		assertThat(after.outboxPending()).isEqualTo(before.outboxPending() + 1);
		assertThat(after.outboxFailed()).isEqualTo(before.outboxFailed() + 1);
		assertThat(after.outboxProcessing()).isEqualTo(before.outboxProcessing() + 1);
		assertThat(after.outboxExpiredLeases()).isEqualTo(before.outboxExpiredLeases() + 1);
		assertThat(after.webhookPending()).isEqualTo(before.webhookPending() + 1);
		assertThat(after.webhookFailed()).isEqualTo(before.webhookFailed() + 1);
		assertThat(after.webhookProcessing()).isEqualTo(before.webhookProcessing() + 1);
		assertThat(after.webhookExpiredLeases()).isEqualTo(before.webhookExpiredLeases() + 1);

		assertThat(gauge("payflow.outbox.events", "status", "pending")).isEqualTo(after.outboxPending());
		assertThat(gauge("payflow.outbox.events", "status", "failed")).isEqualTo(after.outboxFailed());
		assertThat(gauge("payflow.outbox.expired_leases", "pipeline", "outbox"))
				.isEqualTo(after.outboxExpiredLeases());
		assertThat(gauge("payflow.webhook.deliveries.backlog", "status", "pending"))
				.isEqualTo(after.webhookPending());
		assertThat(gauge("payflow.webhook.deliveries.backlog", "status", "failed"))
				.isEqualTo(after.webhookFailed());
		assertThat(gauge("payflow.webhook.expired_leases", "pipeline", "webhook"))
				.isEqualTo(after.webhookExpiredLeases());
	}

	@Test
	void outcomeCountersAndLatencyTimersAdvance() {
		double successBefore = counter("payflow.webhook.deliveries", "outcome", "success");
		double failureBefore = counter("payflow.webhook.deliveries", "outcome", "failure");
		long publishSuccessBefore = timerCount("payflow.outbox.publish.latency", "outcome", "success");
		long publishFailureBefore = timerCount("payflow.outbox.publish.latency", "outcome", "failure");
		long deliverySuccessBefore = timerCount("payflow.webhook.delivery.latency", "outcome", "success");
		long deliveryFailureBefore = timerCount("payflow.webhook.delivery.latency", "outcome", "failure");
		long retryBefore = timerCount("payflow.webhook.delivery.latency", "outcome", "retry");

		metrics.recordWebhookDelivery(Duration.ofMillis(15), true);
		metrics.recordWebhookDelivery(Duration.ofMillis(40), false);
		metrics.recordWebhookRetry(Duration.ofMillis(8));
		metrics.recordOutboxPublish(Duration.ofMillis(12), true);
		metrics.recordOutboxPublish(Duration.ofMillis(30), false);

		assertThat(counter("payflow.webhook.deliveries", "outcome", "success")).isEqualTo(successBefore + 1);
		assertThat(counter("payflow.webhook.deliveries", "outcome", "failure")).isEqualTo(failureBefore + 1);
		assertThat(timerCount("payflow.outbox.publish.latency", "outcome", "success"))
				.isEqualTo(publishSuccessBefore + 1);
		assertThat(timerCount("payflow.outbox.publish.latency", "outcome", "failure"))
				.isEqualTo(publishFailureBefore + 1);
		assertThat(timerCount("payflow.webhook.delivery.latency", "outcome", "success"))
				.isEqualTo(deliverySuccessBefore + 1);
		assertThat(timerCount("payflow.webhook.delivery.latency", "outcome", "failure"))
				.isEqualTo(deliveryFailureBefore + 1);
		assertThat(timerCount("payflow.webhook.delivery.latency", "outcome", "retry")).isEqualTo(retryBefore + 1);
	}

	@Test
	void unauthorizedOperationsAccessIsDenied() throws Exception {
		mockMvc.perform(get("/api/v1/internal/events/health"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/metrics"))
				.andExpect(status().isUnauthorized());

		String userToken = jwtTokenService.createAccessToken(UUID.randomUUID(), List.of("USER"));
		mockMvc.perform(get("/api/v1/internal/events/health")
				.header("Authorization", "Bearer " + userToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/actuator/metrics")
				.header("Authorization", "Bearer " + userToken))
				.andExpect(status().isForbidden());

		String secretKey = registerMerchantSecretKey();
		mockMvc.perform(get("/api/v1/internal/events/health")
				.header("Authorization", "Bearer " + secretKey))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminHealthReturnsCountsWithoutSecrets() throws Exception {
		UUID merchantId = seedMerchant();
		UUID endpointId = seedEndpoint(merchantId);
		insertOutbox(UUID.randomUUID(), merchantId, "FAILED", 3, null, null, Instant.now());
		insertDelivery(UUID.randomUUID(), UUID.randomUUID(), endpointId, merchantId, "FAILED", 3, null, null);

		String adminToken = jwtTokenService.createAccessToken(UUID.randomUUID(), List.of("ADMIN"));
		MvcResult result = mockMvc.perform(get("/api/v1/internal/events/health")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outbox.pending").isNumber())
				.andExpect(jsonPath("$.outbox.failed").isNumber())
				.andExpect(jsonPath("$.outbox.expiredLeases").isNumber())
				.andExpect(jsonPath("$.webhooks.pending").isNumber())
				.andExpect(jsonPath("$.webhooks.failed").isNumber())
				.andExpect(jsonPath("$.webhooks.expiredLeases").isNumber())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain(PAYLOAD_MARKER, URL_MARKER, SECRET_MARKER, "signingSecret", "payload");
		JsonNode json = jsonMapper.readTree(body);
		assertThat(json.size()).isEqualTo(2);
		assertThat(json.get("outbox").get("failed").isNumber()).isTrue();
		assertThat(json.get("webhooks").get("expiredLeases").isNumber()).isTrue();
		assertThat(json.get("outbox").has("payload")).isFalse();
		assertThat(json.get("webhooks").has("url")).isFalse();

		MvcResult metrics = mockMvc.perform(get("/actuator/metrics/payflow.outbox.events")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(metrics.getResponse().getContentAsString())
				.doesNotContain(PAYLOAD_MARKER, URL_MARKER, SECRET_MARKER);
	}

	@Test
	void failedOutboxAndDeliveryCanBeRequeuedWithoutTouchingLedger() {
		UUID merchantId = seedMerchant();
		UUID endpointId = seedEndpoint(merchantId);
		UUID eventId = UUID.randomUUID();
		UUID deliveryId = UUID.randomUUID();
		UUID domainEventId = UUID.randomUUID();
		insertOutbox(eventId, merchantId, "FAILED", 8, null, null, Instant.now());
		insertDelivery(deliveryId, domainEventId, endpointId, merchantId, "FAILED", 8, null, null);
		long ledgerBefore = count("SELECT COUNT(*) FROM ledger_transactions");
		long entriesBefore = count("SELECT COUNT(*) FROM ledger_entries");
		UUID actor = UUID.randomUUID();

		assertThat(recoveryService.requeueFailedOutboxEvent(eventId, actor)).isTrue();
		assertThat(recoveryService.requeueFailedWebhookDelivery(deliveryId, actor)).isTrue();
		assertThat(recoveryService.requeueFailedOutboxEvent(eventId, actor)).isFalse();

		assertThat(statusOf("outbox_events", eventId)).isEqualTo("PENDING");
		assertThat(attemptCount("outbox_events", eventId)).isZero();
		assertThat(claimToken("outbox_events", eventId)).isNull();
		assertThat(jdbcTemplate.queryForObject("SELECT id FROM outbox_events WHERE id = ?", UUID.class, eventId))
				.isEqualTo(eventId);
		assertThat(jdbcTemplate.queryForObject("SELECT payload::text FROM outbox_events WHERE id = ?",
				String.class, eventId)).contains(PAYLOAD_MARKER);

		assertThat(statusOf("webhook_deliveries", deliveryId)).isEqualTo("PENDING");
		assertThat(attemptCount("webhook_deliveries", deliveryId)).isZero();
		assertThat(claimToken("webhook_deliveries", deliveryId)).isNull();
		assertThat(count("""
				SELECT COUNT(*) FROM webhook_deliveries WHERE event_id = ? AND endpoint_id = ?
				""", domainEventId, endpointId)).isEqualTo(1);

		String outboxDetail = jdbcTemplate.queryForObject(
				"SELECT detail FROM security_audit_events WHERE event_type = 'outbox_event_requeued' AND detail = ?",
				String.class, "eventId=" + eventId);
		assertThat(outboxDetail).isEqualTo("eventId=" + eventId).doesNotContain(PAYLOAD_MARKER, SECRET_MARKER);
		assertThat(count("SELECT COUNT(*) FROM ledger_transactions")).isEqualTo(ledgerBefore);
		assertThat(count("SELECT COUNT(*) FROM ledger_entries")).isEqualTo(entriesBefore);
	}

	@Test
	void inFlightEventsCannotBeRequeued() {
		UUID merchantId = seedMerchant();
		UUID endpointId = seedEndpoint(merchantId);
		UUID eventId = UUID.randomUUID();
		UUID token = UUID.randomUUID();
		Instant lease = Instant.now().plusSeconds(60);
		insertOutbox(eventId, merchantId, "PROCESSING", 2, token, lease, Instant.now());
		UUID deliveryId = UUID.randomUUID();
		UUID deliveryToken = UUID.randomUUID();
		insertDelivery(deliveryId, UUID.randomUUID(), endpointId, merchantId, "PROCESSING", 2, deliveryToken,
				lease);

		assertThat(recoveryService.requeueFailedOutboxEvent(eventId, UUID.randomUUID())).isFalse();
		assertThat(recoveryService.requeueFailedWebhookDelivery(deliveryId, UUID.randomUUID())).isFalse();
		assertThat(statusOf("outbox_events", eventId)).isEqualTo("PROCESSING");
		assertThat(claimToken("outbox_events", eventId)).isEqualTo(token);
		assertThat(statusOf("webhook_deliveries", deliveryId)).isEqualTo("PROCESSING");
		assertThat(claimToken("webhook_deliveries", deliveryId)).isEqualTo(deliveryToken);
		assertThat(count(
				"SELECT COUNT(*) FROM security_audit_events WHERE detail = ?", "eventId=" + eventId)).isZero();
	}

	@Test
	void concurrentRequeueAttemptsMoveTheRowOnce() throws Exception {
		UUID merchantId = seedMerchant();
		UUID eventId = UUID.randomUUID();
		insertOutbox(eventId, merchantId, "FAILED", 8, null, null, Instant.now());
		UUID actor = UUID.randomUUID();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger wins = new AtomicInteger();
		try {
			Future<Boolean> first = pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return recoveryService.requeueFailedOutboxEvent(eventId, actor);
			});
			Future<Boolean> second = pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return recoveryService.requeueFailedOutboxEvent(eventId, actor);
			});
			start.countDown();
			if (first.get(15, TimeUnit.SECONDS)) {
				wins.incrementAndGet();
			}
			if (second.get(15, TimeUnit.SECONDS)) {
				wins.incrementAndGet();
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(wins.get()).isEqualTo(1);
		assertThat(statusOf("outbox_events", eventId)).isEqualTo("PENDING");
		assertThat(attemptCount("outbox_events", eventId)).isZero();
		assertThat(count(
				"SELECT COUNT(*) FROM security_audit_events WHERE event_type = 'outbox_event_requeued' AND detail = ?",
				"eventId=" + eventId)).isEqualTo(1);
	}

	@Test
	void expiredLeaseRecoveryStillReclaimsAndStaleTokenCannotPublish() {
		UUID merchantId = seedMerchant();
		UUID eventId = UUID.randomUUID();
		UUID oldToken = UUID.randomUUID();
		insertOutbox(eventId, merchantId, "PROCESSING", 1, oldToken, Instant.parse("1970-01-02T00:00:00Z"),
				Instant.parse("1970-01-02T00:00:00Z"));

		assertThat(recoveryService.requeueFailedOutboxEvent(eventId, UUID.randomUUID())).isFalse();

		List<OutboxEventEntity> claimed = claimRepository.claimBatch(1, Duration.ofSeconds(30), Instant.now(), 8);
		OutboxEventEntity reclaimed = claimed.stream()
				.filter(event -> event.getId().equals(eventId))
				.findFirst()
				.orElseThrow();
		assertThat(reclaimed.getClaimToken()).isNotEqualTo(oldToken);
		assertThat(reclaimed.getAttemptCount()).isEqualTo(2);
		assertThat(claimRepository.markPublished(eventId, oldToken, Instant.now())).isFalse();
		assertThat(claimRepository.markPublished(eventId, reclaimed.getClaimToken(), Instant.now())).isTrue();
		assertThat(statusOf("outbox_events", eventId)).isEqualTo("PUBLISHED");
	}

	@Test
	void logReasonRedactsSecrets() {
		assertThat(OutcomeLogs.reason("failed whsec_abc123 sk_test_zzz\nbearer eyJraw"))
				.doesNotContain("whsec_abc123", "sk_test_zzz", "eyJraw")
				.contains("[redacted]");
	}

	private double gauge(String name, String tag, String value) {
		return meterRegistry.get(name).tag(tag, value).gauge().value();
	}

	private double counter(String name, String tag, String value) {
		return meterRegistry.get(name).tag(tag, value).counter().count();
	}

	private long timerCount(String name, String tag, String value) {
		return meterRegistry.get(name).tag(tag, value).timer().count();
	}

	private String registerMerchantSecretKey() throws Exception {
		String email = "ops-" + UUID.randomUUID() + "@example.com";
		String password = "Str0ngPass!";
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fullName":"Ops User","email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isCreated());
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		String access = jsonMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
		MvcResult merchant = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + access)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Ops Merchant"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		String merchantId = jsonMapper.readTree(merchant.getResponse().getContentAsString()).get("id").asText();
		MvcResult key = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + access)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Ops key","type":"SECRET"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonMapper.readTree(key.getResponse().getContentAsString()).get("secret").asText();
	}

	private UUID seedMerchant() {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		String email = "ops-merchant-" + userId + "@example.com";
		jdbcTemplate.update("""
				INSERT INTO users (id, full_name, email, email_normalized, password_hash)
				VALUES (?, 'Ops', ?, ?, 'hash')
				""", userId, email, email);
		jdbcTemplate.update("""
				INSERT INTO merchants (id, name, status, owner_user_id)
				VALUES (?, 'Ops Merchant', 'ACTIVE', ?)
				""", merchantId, userId);
		return merchantId;
	}

	private UUID seedEndpoint(UUID merchantId) {
		UUID endpointId = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO webhook_endpoints (
				  id, merchant_id, url, signing_secret_ciphertext, secret_prefix, enabled,
				  subscribed_event_types, secret_rotated_at)
				VALUES (?, ?, ?, ?, 'whsec_test', TRUE, CAST('["payment.created"]' AS jsonb), NOW())
				""", endpointId, merchantId, URL_MARKER, "cipher-" + SECRET_MARKER);
		return endpointId;
	}

	private void insertOutbox(UUID id, UUID merchantId, String status, int attempts, UUID claimToken,
			Instant claimExpiresAt, Instant createdAt) {
		jdbcTemplate.update("""
				INSERT INTO outbox_events (
				  id, aggregate_type, aggregate_id, event_type, event_schema_version,
				  merchant_id, payload, status, attempt_count, next_attempt_at, created_at,
				  claim_token, claim_expires_at, last_error)
				VALUES (?, 'PAYMENT', ?, 'payment.created', 1, ?, CAST(? AS jsonb),
				        ?, ?, ?, ?, ?, ?, 'max_attempts_exhausted')
				""",
				id, UUID.randomUUID(), merchantId, "{\"marker\":\"" + PAYLOAD_MARKER + "\"}",
				status, attempts, Timestamp.from(createdAt), Timestamp.from(createdAt),
				claimToken, claimExpiresAt == null ? null : Timestamp.from(claimExpiresAt));
	}

	private void insertDelivery(UUID id, UUID eventId, UUID endpointId, UUID merchantId, String status,
			int attempts, UUID claimToken, Instant claimExpiresAt) {
		Instant now = Instant.now();
		jdbcTemplate.update("""
				INSERT INTO webhook_deliveries (
				  id, event_id, endpoint_id, merchant_id, event_type, payload, status,
				  attempt_count, next_attempt_at, created_at, claim_token, claim_expires_at, last_error)
				VALUES (?, ?, ?, ?, 'payment.created', CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, 'http_500')
				""",
				id, eventId, endpointId, merchantId, "{\"marker\":\"" + PAYLOAD_MARKER + "\"}",
				status, attempts, Timestamp.from(now), Timestamp.from(now),
				claimToken, claimExpiresAt == null ? null : Timestamp.from(claimExpiresAt));
	}

	private String statusOf(String table, UUID id) {
		return jdbcTemplate.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
	}

	private int attemptCount(String table, UUID id) {
		Integer attempts = jdbcTemplate.queryForObject(
				"SELECT attempt_count FROM " + table + " WHERE id = ?", Integer.class, id);
		return attempts == null ? -1 : attempts;
	}

	private UUID claimToken(String table, UUID id) {
		return jdbcTemplate.queryForObject("SELECT claim_token FROM " + table + " WHERE id = ?", UUID.class, id);
	}

	private long count(String sql, Object... args) {
		Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}
}
