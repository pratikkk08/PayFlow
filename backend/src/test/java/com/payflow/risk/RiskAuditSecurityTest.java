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
import com.payflow.payments.service.PaymentService;
import com.payflow.risk.service.RiskEvaluationService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Audit/security hardening coverage for risk-aware authorization (Task 6.5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RiskAuditSecurityTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void oneAssessmentAndOneAttemptPerPayment_andSnapshotImmutableAfterRuleChange() throws Exception {
		String token = registerLogin("audit-snap-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		UUID ruleId = insertRule(UUID.fromString(merchantId), "AMOUNT_THRESHOLD",
				"{\"thresholdMinor\":100}", "FLAG", 1);
		String paymentId = createPayment(token, merchantId, "audit-snap-1", 5000);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));

		assertThat(countAssessments(paymentId)).isEqualTo(1);
		assertThat(countAttempts(paymentId)).isEqualTo(1);

		Map<String, Object> matchBefore = jdbcTemplate.queryForMap(
				"""
						SELECT rule_type, matched_action, reason_code, score_contribution
						FROM risk_rule_matches m
						JOIN risk_assessments a ON a.id = m.assessment_id
						WHERE a.payment_id = ?
						""",
				UUID.fromString(paymentId));
		assertThat(matchBefore.get("rule_type")).isEqualTo("AMOUNT_THRESHOLD");
		assertThat(matchBefore.get("matched_action")).isEqualTo("FLAG");
		assertThat(matchBefore.get("reason_code"))
				.isEqualTo(RiskEvaluationService.REASON_AMOUNT_ABOVE_THRESHOLD);
		assertThat(((Number) matchBefore.get("score_contribution")).intValue()).isEqualTo(25);

		jdbcTemplate.update(
				"""
						UPDATE risk_rules
						SET parameters = CAST('{"thresholdMinor":999999}' AS jsonb),
						    action = 'BLOCK', enabled = FALSE, version = version + 1
						WHERE id = ?
						""",
				ruleId);

		Map<String, Object> matchAfter = jdbcTemplate.queryForMap(
				"""
						SELECT rule_type, matched_action, reason_code, score_contribution
						FROM risk_rule_matches m
						JOIN risk_assessments a ON a.id = m.assessment_id
						WHERE a.payment_id = ?
						""",
				UUID.fromString(paymentId));
		assertThat(matchAfter).isEqualTo(matchBefore);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT decision FROM risk_assessments WHERE payment_id = ?",
				String.class, UUID.fromString(paymentId))).isEqualTo("FLAG");
	}

	@Test
	void blockStaysFailedWithSingleFailedEvent_andCannotReauthorize() throws Exception {
		String token = registerLogin("audit-block-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		insertRule(UUID.fromString(merchantId), "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "BLOCK", 1);
		String paymentId = createPayment(token, merchantId, "audit-block-1", 8000);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureCode").value(PaymentService.FAILURE_RISK_BLOCKED));

		assertThat(countOutbox(paymentId, "payment.failed")).isEqualTo(1);
		assertThat(countOutbox(paymentId, "payment.authorized")).isZero();
		assertThat(countLedger(paymentId)).isZero();

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("invalid_state_transition"));

		assertThat(countAssessments(paymentId)).isEqualTo(1);
		assertThat(countAttempts(paymentId)).isEqualTo(1);
		assertThat(countOutbox(paymentId, "payment.failed")).isEqualTo(1);
	}

	@Test
	void malformedBlockRuleOnAuthorize_failClosedNotAllow() throws Exception {
		String token = registerLogin("audit-mal-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		insertRule(UUID.fromString(merchantId), "AMOUNT_THRESHOLD", "{\"wrong\":true}", "BLOCK", 1);
		String paymentId = createPayment(token, merchantId, "audit-mal-1", 500);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureCode").value(PaymentService.FAILURE_RISK_BLOCKED));

		assertThat(jdbcTemplate.queryForObject(
				"SELECT decision FROM risk_assessments WHERE payment_id = ?",
				String.class, UUID.fromString(paymentId))).isEqualTo("BLOCK");
		assertThat(jdbcTemplate.queryForObject(
				"""
						SELECT reason_code FROM risk_rule_matches m
						JOIN risk_assessments a ON a.id = m.assessment_id
						WHERE a.payment_id = ?
						""",
				String.class, UUID.fromString(paymentId)))
				.isEqualTo(RiskEvaluationService.REASON_RULE_CONFIG_INVALID);
	}

	@Test
	void merchantIsolation_rulesAttemptsAndAssessments() throws Exception {
		String ownerA = registerLogin("audit-a-" + UUID.randomUUID() + "@example.com");
		String ownerB = registerLogin("audit-b-" + UUID.randomUUID() + "@example.com");
		String merchantA = createMerchant(ownerA);
		String merchantB = createMerchant(ownerB);
		insertRule(UUID.fromString(merchantB), "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "BLOCK", 1);

		String paymentA = createPayment(ownerA, merchantA, "iso-a", 9000);
		mockMvc.perform(post("/api/v1/payments/" + paymentA + "/authorize")
				.header("Authorization", "Bearer " + ownerA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));

		assertThat(jdbcTemplate.queryForObject(
				"SELECT decision FROM risk_assessments WHERE payment_id = ?",
				String.class, UUID.fromString(paymentA))).isEqualTo("ALLOW");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT merchant_id FROM risk_assessments WHERE payment_id = ?",
				UUID.class, UUID.fromString(paymentA))).isEqualTo(UUID.fromString(merchantA));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT merchant_id FROM risk_authorization_attempts WHERE payment_id = ?",
				UUID.class, UUID.fromString(paymentA))).isEqualTo(UUID.fromString(merchantA));
		assertThat(countAssessmentsForMerchant(merchantB)).isZero();
		assertThat(countAttemptsForMerchant(merchantB)).isZero();
	}

	@Test
	void apiKeyForgedAndUnauthenticatedCannotAuthorizeOrWriteRisk() throws Exception {
		String owner = registerLogin("audit-sec-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(owner);
		String paymentId = createPayment(owner, merchantId, "sec-1", 1500);
		String secret = createSecretApiKey(owner, merchantId);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + secret))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer sk_test_forged_not_a_real_key"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJmYWtlIn0.sig"))
				.andExpect(status().isUnauthorized());

		assertThat(countAssessments(paymentId)).isZero();
		assertThat(countAttempts(paymentId)).isZero();
		assertThat(paymentStatus(paymentId)).isEqualTo("CREATED");
	}

	@Test
	void concurrentAuthorizeAndCancel_noDeadlockOrDuplicateRiskRows() throws Exception {
		String token = registerLogin("audit-race-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		String paymentId = createPayment(token, merchantId, "race-1", 1600);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();
		futures.add(pool.submit(() -> {
			ready.countDown();
			start.await(30, TimeUnit.SECONDS);
			return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
					.header("Authorization", "Bearer " + token)).andReturn();
		}));
		futures.add(pool.submit(() -> {
			ready.countDown();
			start.await(30, TimeUnit.SECONDS);
			return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
					.header("Authorization", "Bearer " + token)).andReturn();
		}));
		assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		int ok = 0;
		int conflict = 0;
		for (Future<MvcResult> future : futures) {
			int status = future.get(60, TimeUnit.SECONDS).getResponse().getStatus();
			assertThat(status).isNotEqualTo(500);
			if (status == 200) {
				ok++;
			}
			else if (status == 409) {
				conflict++;
			}
		}
		pool.shutdown();
		assertThat(ok).isGreaterThanOrEqualTo(1);
		assertThat(ok + conflict).isEqualTo(2);

		String status = paymentStatus(paymentId);
		assertThat(status).isIn("AUTHORIZED", "CANCELLED");
		assertThat(countAssessments(paymentId)).isLessThanOrEqualTo(1);
		assertThat(countAttempts(paymentId)).isLessThanOrEqualTo(1);
		if ("AUTHORIZED".equals(status)) {
			assertThat(countAssessments(paymentId)).isEqualTo(1);
			assertThat(countAttempts(paymentId)).isEqualTo(1);
			assertThat(countLedger(paymentId)).isZero();
		}
		if ("CANCELLED".equals(status)) {
			assertThat(countOutbox(paymentId, "payment.cancelled")).isEqualTo(1);
		}
	}

	private int countAssessments(String paymentId) {
		Integer c = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM risk_assessments WHERE payment_id = ?",
				Integer.class, UUID.fromString(paymentId));
		return c == null ? 0 : c;
	}

	private int countAttempts(String paymentId) {
		Integer c = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM risk_authorization_attempts WHERE payment_id = ?",
				Integer.class, UUID.fromString(paymentId));
		return c == null ? 0 : c;
	}

	private int countAssessmentsForMerchant(String merchantId) {
		Integer c = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM risk_assessments WHERE merchant_id = ?",
				Integer.class, UUID.fromString(merchantId));
		return c == null ? 0 : c;
	}

	private int countAttemptsForMerchant(String merchantId) {
		Integer c = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM risk_authorization_attempts WHERE merchant_id = ?",
				Integer.class, UUID.fromString(merchantId));
		return c == null ? 0 : c;
	}

	private int countOutbox(String paymentId, String eventType) {
		Integer c = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = ?",
				Integer.class, UUID.fromString(paymentId), eventType);
		return c == null ? 0 : c;
	}

	private int countLedger(String paymentId) {
		Integer c = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM ledger_transactions
						WHERE source_type = 'PAYMENT_CAPTURE' AND source_id = ?
						""",
				Integer.class, UUID.fromString(paymentId));
		return c == null ? 0 : c;
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

	private String createSecretApiKey(String token, String merchantId) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Risk audit key","type":"SECRET"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonMapper.readTree(created.getResponse().getContentAsString()).get("secret").asText();
	}

	private String createPayment(String accessToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", "audit-key-" + UUID.randomUUID())
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
						{"name":"Risk Audit Merchant","description":"Audit security test"}
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
