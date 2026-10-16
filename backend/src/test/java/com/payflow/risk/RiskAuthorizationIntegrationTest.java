package com.payflow.risk;

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
import com.payflow.payments.processor.DeterministicSimulatedPaymentProcessor;
import com.payflow.payments.service.PaymentService;
import com.payflow.risk.service.RiskEvaluationService;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RiskAuthorizationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private TestRiskAssessmentWriteFaultInjector assessmentFaultInjector;

	@Test
	void allow_callsProcessorAndPersistsAssessment() throws Exception {
		String token = registerLogin("risk-allow-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		String paymentId = createPayment(token, merchantId, "risk-allow-1", 1500);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));

		assertAssessment(paymentId, merchantId, "ALLOW", 0);
		assertThat(countMatches(paymentId)).isZero();
		assertThat(countOutbox(paymentId, "payment.authorized")).isEqualTo(1);
		assertThat(countOutbox(paymentId, "payment.failed")).isZero();
	}

	@Test
	void flag_callsProcessorAndPersistsAssessmentWithMatches() throws Exception {
		String token = registerLogin("risk-flag-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		UUID ruleId = insertRule(UUID.fromString(merchantId), "AMOUNT_THRESHOLD",
				"{\"thresholdMinor\":1000}", "FLAG", 10);
		String paymentId = createPayment(token, merchantId, "risk-flag-1", 5000);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));

		Map<String, Object> assessment = assertAssessment(paymentId, merchantId, "FLAG", 25);
		UUID assessmentId = (UUID) assessment.get("id");
		List<Map<String, Object>> matches = jdbcTemplate.queryForList(
				"""
						SELECT rule_id, rule_type, matched_action, reason_code, score_contribution
						FROM risk_rule_matches WHERE assessment_id = ?
						""",
				assessmentId);
		assertThat(matches).hasSize(1);
		assertThat(matches.get(0).get("rule_id")).isEqualTo(ruleId);
		assertThat(matches.get(0).get("rule_type")).isEqualTo("AMOUNT_THRESHOLD");
		assertThat(matches.get(0).get("matched_action")).isEqualTo("FLAG");
		assertThat(matches.get(0).get("reason_code"))
				.isEqualTo(RiskEvaluationService.REASON_AMOUNT_ABOVE_THRESHOLD);
		assertThat(matches.get(0).get("score_contribution")).isEqualTo(25);
		assertThat(countOutbox(paymentId, "payment.authorized")).isEqualTo(1);
	}

	@Test
	void review_keepsCreatedWithoutProcessorEventsOrLedger() throws Exception {
		String token = registerLogin("risk-review-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		insertRule(UUID.fromString(merchantId), "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "REVIEW", 1);
		String paymentId = createPayment(token, merchantId, "risk-review-1", 9000);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CREATED"))
				.andExpect(jsonPath("$.failureCode").value(org.hamcrest.Matchers.nullValue()));

		assertAssessment(paymentId, merchantId, "REVIEW", 50);
		assertThat(paymentStatus(paymentId)).isEqualTo("CREATED");
		assertThat(countOutbox(paymentId, "payment.authorized")).isZero();
		assertThat(countOutbox(paymentId, "payment.failed")).isZero();
		assertThat(countLedgerForPayment(paymentId)).isZero();
	}

	@Test
	void block_failsWithRiskBlockedAndSingleFailedOutbox() throws Exception {
		String token = registerLogin("risk-block-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		insertRule(UUID.fromString(merchantId), "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "BLOCK", 1);
		String paymentId = createPayment(token, merchantId, "risk-block-1", 9000);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureCode").value(PaymentService.FAILURE_RISK_BLOCKED));

		assertAssessment(paymentId, merchantId, "BLOCK", 100);
		assertThat(paymentStatus(paymentId)).isEqualTo("FAILED");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT failure_code FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId)))
				.isEqualTo(PaymentService.FAILURE_RISK_BLOCKED);
		assertThat(countOutbox(paymentId, "payment.failed")).isEqualTo(1);
		assertThat(countOutbox(paymentId, "payment.authorized")).isZero();
		assertThat(countLedgerForPayment(paymentId)).isZero();
	}

	@Test
	void existingReviewAssessmentCannotBeBypassed() throws Exception {
		String token = registerLogin("risk-hold-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		insertRule(UUID.fromString(merchantId), "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "REVIEW", 1);
		String paymentId = createPayment(token, merchantId, "risk-hold-1", 5000);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CREATED"));

		// Disable rule so re-eval would ALLOW — must still refuse.
		jdbcTemplate.update("UPDATE risk_rules SET enabled = FALSE WHERE merchant_id = ?",
				UUID.fromString(merchantId));

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("invalid_state_transition"));

		assertThat(paymentStatus(paymentId)).isEqualTo("CREATED");
		assertThat(countAssessments(paymentId)).isEqualTo(1);
	}

	@Test
	void concurrentAuthorizeCannotDuplicateAssessments() throws Exception {
		String token = registerLogin("risk-conc-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		insertRule(UUID.fromString(merchantId), "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "FLAG", 1);
		String paymentId = createPayment(token, merchantId, "risk-conc-1", 2500);

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
				return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
						.header("Authorization", "Bearer " + token))
						.andReturn();
			}));
		}
		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		int success = 0;
		int conflicts = 0;
		for (Future<MvcResult> future : futures) {
			int status = future.get(30, TimeUnit.SECONDS).getResponse().getStatus();
			if (status == 200) {
				success++;
			}
			else if (status == 409) {
				conflicts++;
			}
			else {
				throw new AssertionError("Unexpected status " + status);
			}
		}
		pool.shutdown();

		assertThat(success).isEqualTo(1);
		assertThat(conflicts).isEqualTo(threads - 1);
		assertThat(countAssessments(paymentId)).isEqualTo(1);
		assertThat(paymentStatus(paymentId)).isEqualTo("AUTHORIZED");
	}

	@Test
	void processorDeclinePreservesAssessmentAtomicallyWithFailed() throws Exception {
		String token = registerLogin("risk-proc-fail-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		String paymentId = createPayment(token, merchantId,
				DeterministicSimulatedPaymentProcessor.REF_PREFIX_DECLINE + "risk", 1800);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureCode")
						.value(DeterministicSimulatedPaymentProcessor.FAILURE_SIMULATED_DECLINE));

		assertAssessment(paymentId, merchantId, "ALLOW", 0);
		assertThat(countOutbox(paymentId, "payment.failed")).isEqualTo(1);
	}

	@Test
	void assessmentPersistFailureRollsBackAuthorization() throws Exception {
		String token = registerLogin("risk-write-fail-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		String paymentId = createPayment(token, merchantId, "risk-write-fail-1", 1200);

		assessmentFaultInjector.failNextPersist();
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("risk_assessment_write_failed"));

		assertThat(paymentStatus(paymentId)).isEqualTo("CREATED");
		assertThat(countAssessments(paymentId)).isZero();
		assertThat(countAttempts(paymentId)).isZero();
		assertThat(countOutbox(paymentId, "payment.authorized")).isZero();
		assertThat(countOutbox(paymentId, "payment.failed")).isZero();
	}

	@Test
	void crossMerchantAndUnauthenticatedDoNotWriteAssessments() throws Exception {
		String owner = registerLogin("risk-own-" + UUID.randomUUID() + "@example.com");
		String other = registerLogin("risk-oth-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(owner);
		String paymentId = createPayment(owner, merchantId, "risk-sec-1", 2000);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize"))
				.andExpect(status().isUnauthorized());
		assertThat(countAssessments(paymentId)).isZero();

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + other))
				.andExpect(status().isNotFound());
		assertThat(countAssessments(paymentId)).isZero();
		assertThat(paymentStatus(paymentId)).isEqualTo("CREATED");
	}

	private Map<String, Object> assertAssessment(String paymentId, String merchantId, String decision, int score) {
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"""
						SELECT id, merchant_id, decision, risk_score, configuration_version
						FROM risk_assessments WHERE payment_id = ?
						""",
				UUID.fromString(paymentId));
		assertThat(row.get("merchant_id")).isEqualTo(UUID.fromString(merchantId));
		assertThat(row.get("decision")).isEqualTo(decision);
		assertThat(((Number) row.get("risk_score")).intValue()).isEqualTo(score);
		assertThat(row.get("configuration_version").toString()).isNotBlank();
		return row;
	}

	private int countAssessments(String paymentId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM risk_assessments WHERE payment_id = ?",
				Integer.class, UUID.fromString(paymentId));
		return count == null ? 0 : count;
	}

	private int countAttempts(String paymentId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM risk_authorization_attempts WHERE payment_id = ?",
				Integer.class, UUID.fromString(paymentId));
		return count == null ? 0 : count;
	}

	private int countMatches(String paymentId) {
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM risk_rule_matches m
						JOIN risk_assessments a ON a.id = m.assessment_id
						WHERE a.payment_id = ?
						""",
				Integer.class, UUID.fromString(paymentId));
		return count == null ? 0 : count;
	}

	private int countOutbox(String paymentId, String eventType) {
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM outbox_events
						WHERE aggregate_id = ? AND event_type = ?
						""",
				Integer.class, UUID.fromString(paymentId), eventType);
		return count == null ? 0 : count;
	}

	private int countLedgerForPayment(String paymentId) {
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM ledger_transactions
						WHERE source_type = 'PAYMENT_CAPTURE' AND source_id = ?
						""",
				Integer.class, UUID.fromString(paymentId));
		return count == null ? 0 : count;
	}

	private String paymentStatus(String paymentId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId));
	}

	private UUID insertRule(UUID merchantId, String type, String params, String action, int priority) {
		UUID ruleId = UUID.randomUUID();
		jdbcTemplate.update(
				"""
						INSERT INTO risk_rules (
						  id, merchant_id, rule_type, parameters, action, priority, enabled)
						VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, TRUE)
						""",
				ruleId, merchantId, type, params, action, priority);
		return ruleId;
	}

	private String createPayment(String accessToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", "risk-key-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"%s","amountMinor":%d,"currency":"INR"}
						""".formatted(merchantId, reference, amountMinor)))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonMapper.readTree(created.getResponse().getContentAsString()).get("paymentId").asText();
	}

	private String createMerchant(String accessToken) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Risk Auth Merchant","description":"Risk authorization test"}
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
