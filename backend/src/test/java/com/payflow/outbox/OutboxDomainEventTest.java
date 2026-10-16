package com.payflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.payflow.ledger.TestLedgerPostingFaultInjector;
import com.payflow.payments.processor.DeterministicSimulatedPaymentProcessor;
import com.payflow.refunds.processor.DeterministicSimulatedRefundProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxDomainEventTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private TestOutboxEventWriteFaultInjector outboxFaultInjector;
	@Autowired
	private TestLedgerPostingFaultInjector ledgerFaultInjector;

	@Test
	void paymentCreateAuthorizeFailCaptureCancelAndRefundEmitExpectedEvents() throws Exception {
		String ownerToken = registerLogin("obx-flow-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		String createdId = createPayment(ownerToken, merchantId, "obx-created", 2100, "secret note");
		assertEventCount("payment.created", createdId, 1);
		assertSafePayload("payment.created", createdId, "CREATED");
		assertNoSecretFieldsInPayloads(merchantId);

		mockMvc.perform(post("/api/v1/payments/" + createdId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));
		assertEventCount("payment.authorized", createdId, 1);
		assertEventCount("payment.failed", createdId, 0);

		String declineId = createPayment(ownerToken, merchantId,
				DeterministicSimulatedPaymentProcessor.REF_PREFIX_DECLINE + "obx", 1800, null);
		mockMvc.perform(post("/api/v1/payments/" + declineId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"));
		assertEventCount("payment.failed", declineId, 1);
		assertEventCount("payment.authorized", declineId, 0);
		String failedPayload = payloadJson("payment.failed", declineId);
		assertThat(failedPayload).doesNotContain("failureCode").doesNotContain("simulated");

		mockMvc.perform(post("/api/v1/payments/" + createdId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CAPTURED"));
		assertEventCount("payment.captured", createdId, 1);
		assertLedgerCount("PAYMENT_CAPTURE", createdId, 1);

		String cancelId = createPayment(ownerToken, merchantId, "obx-cancel", 900, null);
		mockMvc.perform(post("/api/v1/payments/" + cancelId + "/cancel")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertEventCount("payment.cancelled", cancelId, 1);

		MvcResult refund = mockMvc.perform(post("/api/v1/payments/" + createdId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "obx-rf-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("obx-ref-ok", 500)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andReturn();
		String refundId = jsonMapper.readTree(refund.getResponse().getContentAsString()).get("refundId").asText();
		assertEventCount("refund.succeeded", refundId, 1);
		assertLedgerCount("REFUND", refundId, 1);
		assertSafeRefundPayload(refundId);

		mockMvc.perform(post("/api/v1/payments/" + createdId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "obx-rf-fail-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody(DeterministicSimulatedRefundProcessor.REF_PREFIX_DECLINE + "obx", 100)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FAILED"));
		Integer refundFailedEvents = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM outbox_events
						WHERE event_type = 'refund.failed' AND merchant_id = ?
						""",
				Integer.class, UUID.fromString(merchantId));
		assertThat(refundFailedEvents).isZero();
	}

	@Test
	void idempotentReplayDoesNotCreateDuplicateEvents() throws Exception {
		String ownerToken = registerLogin("obx-idem-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String key = "obx-idem-" + UUID.randomUUID();
		String body = """
				{"merchantId":"%s","merchantReference":"obx-idem-ref","amountMinor":1500,"currency":"INR"}
				""".formatted(merchantId);

		MvcResult first = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		String paymentId = jsonMapper.readTree(first.getResponse().getContentAsString()).get("paymentId").asText();

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.paymentId").value(paymentId));

		assertEventCount("payment.created", paymentId, 1);
	}

	@Test
	void concurrentCaptureEmitsSingleCapturedEvent() throws Exception {
		String ownerToken = registerLogin("obx-conc-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createAuthorizedPayment(ownerToken, merchantId, "obx-conc-cap", 3300);

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					start.await(5, TimeUnit.SECONDS);
					return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
							.header("Authorization", "Bearer " + ownerToken))
							.andReturn()
							.getResponse()
							.getStatus();
				}));
			}
			start.countDown();
			int ok = 0;
			int conflict = 0;
			for (Future<Integer> future : futures) {
				int status = future.get(15, TimeUnit.SECONDS);
				if (status == 200) {
					ok++;
				}
				else if (status == 409) {
					conflict++;
				}
			}
			assertThat(ok).isEqualTo(1);
			assertThat(conflict).isEqualTo(threads - 1);
		}
		finally {
			pool.shutdownNow();
		}

		assertEventCount("payment.captured", paymentId, 1);
		assertLedgerCount("PAYMENT_CAPTURE", paymentId, 1);
	}

	@Test
	void forcedOutboxFailureRollsBackCaptureDomainAndLedger() throws Exception {
		String ownerToken = registerLogin("obx-roll-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createAuthorizedPayment(ownerToken, merchantId, "obx-roll-cap", 1200);

		outboxFaultInjector.failNextWrite();
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("outbox_write_failed"));

		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId));
		assertThat(status).isEqualTo("AUTHORIZED");
		assertLedgerCount("PAYMENT_CAPTURE", paymentId, 0);
		assertEventCount("payment.captured", paymentId, 0);
	}

	@Test
	void forcedLedgerFailureLeavesNoCommittedOutboxEvent() throws Exception {
		String ownerToken = registerLogin("obx-led-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createAuthorizedPayment(ownerToken, merchantId, "obx-led-cap", 1400);

		ledgerFaultInjector.failNextPost();
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("ledger_posting_failed"));

		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId));
		assertThat(status).isEqualTo("AUTHORIZED");
		assertLedgerCount("PAYMENT_CAPTURE", paymentId, 0);
		assertEventCount("payment.captured", paymentId, 0);
	}

	@Test
	void crossMerchantAndInvalidTransitionsCreateNoEvents() throws Exception {
		String ownerToken = registerLogin("obx-sec-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("obx-other-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createAuthorizedPayment(ownerToken, merchantId, "obx-sec", 1600);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound());
		assertEventCount("payment.captured", paymentId, 0);
		assertLedgerCount("PAYMENT_CAPTURE", paymentId, 0);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isConflict());
		assertEventCount("payment.authorized", paymentId, 1);

		String createdOnly = createPayment(ownerToken, merchantId, "obx-nocap", 700, null);
		mockMvc.perform(post("/api/v1/payments/" + createdOnly + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "obx-bad-rf-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("obx-bad-ref", 100)))
				.andExpect(status().isConflict());
		Integer refundEvents = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM outbox_events
						WHERE aggregate_type = 'REFUND' AND merchant_id = ?
						""",
				Integer.class, UUID.fromString(merchantId));
		assertThat(refundEvents).isZero();
	}

	private void assertSafePayload(String eventType, String paymentId, String status) {
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"""
						SELECT status AS outbox_status, event_schema_version, payload::text AS payload
						FROM outbox_events
						WHERE event_type = ? AND aggregate_id = ?
						""",
				eventType, UUID.fromString(paymentId));
		assertThat(row.get("outbox_status")).isEqualTo("PENDING");
		assertThat(((Number) row.get("event_schema_version")).intValue()).isEqualTo(1);
		JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
		assertThat(payload.get("paymentId").asText()).isEqualTo(paymentId);
		assertThat(payload.get("status").asText()).isEqualTo(status);
		assertThat(payload.get("amountMinor").asLong()).isPositive();
		assertThat(payload.get("currency").asText()).isEqualTo("INR");
		assertThat(payload.has("createdAt")).isTrue();
		assertThat(payload.has("merchantReference")).isFalse();
		assertThat(payload.has("description")).isFalse();
		assertThat(payload.has("failureCode")).isFalse();
		assertThat(payload.has("processorRef")).isFalse();
	}

	private void assertSafeRefundPayload(String refundId) {
		JsonNode payload = jsonMapper.readTree(payloadJson("refund.succeeded", refundId));
		assertThat(payload.get("refundId").asText()).isEqualTo(refundId);
		assertThat(payload.get("status").asText()).isEqualTo("SUCCEEDED");
		assertThat(payload.has("merchantReference")).isFalse();
		assertThat(payload.has("failureCode")).isFalse();
		assertThat(payload.has("reason")).isFalse();
	}

	private void assertNoSecretFieldsInPayloads(String merchantId) {
		List<String> payloads = jdbcTemplate.query(
				"SELECT payload::text FROM outbox_events WHERE merchant_id = ?",
				(rs, rowNum) -> rs.getString(1),
				UUID.fromString(merchantId));
		for (String payload : payloads) {
			assertThat(payload).doesNotContain("password")
					.doesNotContain("accessToken")
					.doesNotContain("sk_test_")
					.doesNotContain("Idempotency")
					.doesNotContain("secret note")
					.doesNotContain("merchantReference")
					.doesNotContain("failureCode");
		}
	}

	private void assertEventCount(String eventType, String aggregateId, int expected) {
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM outbox_events
						WHERE event_type = ? AND aggregate_id = ? AND status = 'PENDING'
						""",
				Integer.class, eventType, UUID.fromString(aggregateId));
		assertThat(count).isEqualTo(expected);
	}

	private String payloadJson(String eventType, String aggregateId) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT payload::text FROM outbox_events
						WHERE event_type = ? AND aggregate_id = ?
						""",
				String.class, eventType, UUID.fromString(aggregateId));
	}

	private void assertLedgerCount(String sourceType, String sourceId, int expected) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ledger_transactions WHERE source_type = ? AND source_id = ?",
				Integer.class, sourceType, UUID.fromString(sourceId));
		assertThat(count).isEqualTo(expected);
	}

	private static String refundBody(String reference, long amountMinor) {
		return """
				{"merchantReference":"%s","amountMinor":%d}
				""".formatted(reference, amountMinor);
	}

	private String createAuthorizedPayment(String ownerToken, String merchantId, String reference, long amount)
			throws Exception {
		String paymentId = createPayment(ownerToken, merchantId, reference, amount, null);
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));
		return paymentId;
	}

	private String createPayment(String accessToken, String merchantId, String reference, long amountMinor,
			String description) throws Exception {
		String descField = description == null ? "" : ",\"description\":\"" + description + "\"";
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", "obx-pay-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"%s","amountMinor":%d,"currency":"INR"%s}
						""".formatted(merchantId, reference, amountMinor, descField)))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonMapper.readTree(created.getResponse().getContentAsString()).get("paymentId").asText();
	}

	private String createMerchant(String accessToken) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Outbox Event Merchant","description":"Outbox domain events"}
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
