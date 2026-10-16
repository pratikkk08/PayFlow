package com.payflow.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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
import com.payflow.payments.processor.DeterministicSimulatedPaymentProcessor;
import com.payflow.payments.service.PaymentService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real Postgres concurrency tests for merchant-scoped risk velocity / failure thresholds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RiskVelocityConcurrencyTest {

	private static final int THREADS = 6;
	private static final long TIMEOUT_SEC = 60;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void concurrentAuthorizes_respectVelocityMaxCountBoundary() throws Exception {
		// maxCount=2 → first two peer-attempt counts are 0 then 1 (ALLOW); rest see >=2 (BLOCK).
		String token = registerLogin("vel-conc-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		insertRule(UUID.fromString(merchantId), "PAYMENT_VELOCITY",
				"{\"maxCount\":2,\"windowSeconds\":3600}", "BLOCK", 1);

		List<String> paymentIds = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			paymentIds.add(createPayment(token, merchantId, "vel-c-" + i, 1000 + i));
		}

		AuthorizeStats stats = authorizeAllConcurrently(token, paymentIds);

		assertThat(stats.http500).isZero();
		assertThat(stats.authorized).isEqualTo(2);
		assertThat(stats.riskBlocked).isEqualTo(THREADS - 2);
		assertThat(countAssessmentsForMerchant(merchantId)).isEqualTo(THREADS);
		assertThat(countAttemptsForMerchant(merchantId)).isEqualTo(THREADS);
		assertThat(countOutboxForMerchant(merchantId, "payment.authorized")).isEqualTo(2);
		assertThat(countOutboxForMerchant(merchantId, "payment.failed")).isEqualTo(THREADS - 2);
		assertThat(countLedgerForMerchant(merchantId)).isZero();
	}

	@Test
	void differentMerchants_evaluateIndependentlyUnderConcurrency() throws Exception {
		String tokenA = registerLogin("vel-ma-" + UUID.randomUUID() + "@example.com");
		String tokenB = registerLogin("vel-mb-" + UUID.randomUUID() + "@example.com");
		String merchantA = createMerchant(tokenA);
		String merchantB = createMerchant(tokenB);
		insertRule(UUID.fromString(merchantA), "PAYMENT_VELOCITY",
				"{\"maxCount\":1,\"windowSeconds\":3600}", "BLOCK", 1);
		insertRule(UUID.fromString(merchantB), "PAYMENT_VELOCITY",
				"{\"maxCount\":1,\"windowSeconds\":3600}", "BLOCK", 1);

		String payA = createPayment(tokenA, merchantA, "ind-a", 1100);
		String payB = createPayment(tokenB, merchantB, "ind-b", 1200);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Future<MvcResult> fa = pool.submit(() -> {
			ready.countDown();
			start.await(TIMEOUT_SEC, TimeUnit.SECONDS);
			return mockMvc.perform(post("/api/v1/payments/" + payA + "/authorize")
					.header("Authorization", "Bearer " + tokenA)).andReturn();
		});
		Future<MvcResult> fb = pool.submit(() -> {
			ready.countDown();
			start.await(TIMEOUT_SEC, TimeUnit.SECONDS);
			return mockMvc.perform(post("/api/v1/payments/" + payB + "/authorize")
					.header("Authorization", "Bearer " + tokenB)).andReturn();
		});
		assertThat(ready.await(TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		assertThat(fa.get(TIMEOUT_SEC, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
		assertThat(fb.get(TIMEOUT_SEC, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
		pool.shutdown();

		assertThat(paymentStatus(payA)).isEqualTo("AUTHORIZED");
		assertThat(paymentStatus(payB)).isEqualTo("AUTHORIZED");
		assertThat(countAssessmentsForMerchant(merchantA)).isEqualTo(1);
		assertThat(countAssessmentsForMerchant(merchantB)).isEqualTo(1);
	}

	@Test
	void identicalPaymentRetry_doesNotInflateAttemptCounts() throws Exception {
		String token = registerLogin("vel-retry-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		// Hold via amount REVIEW so the first authorize records exactly one attempt.
		insertRule(UUID.fromString(merchantId), "AMOUNT_THRESHOLD",
				"{\"thresholdMinor\":100}", "REVIEW", 1);
		String paymentId = createPayment(token, merchantId, "retry-1", 1300);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		assertThat(paymentStatus(paymentId)).isEqualTo("CREATED"); // REVIEW hold
		assertThat(countAttemptsForMerchant(merchantId)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict());

		assertThat(countAttemptsForMerchant(merchantId)).isEqualTo(1);
		assertThat(countAssessmentsForMerchant(merchantId)).isEqualTo(1);
	}

	@Test
	void concurrentAuthorizes_respectRepeatedFailureThreshold() throws Exception {
		// maxFailures=1: first sim_decline becomes FAILED; subsequent see >=1 → risk_blocked.
		String token = registerLogin("fail-conc-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		insertRule(UUID.fromString(merchantId), "REPEATED_FAILURES",
				"{\"maxFailures\":1,\"windowSeconds\":3600}", "BLOCK", 1);

		List<String> paymentIds = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			paymentIds.add(createPayment(token, merchantId,
					DeterministicSimulatedPaymentProcessor.REF_PREFIX_DECLINE + "c" + i, 1400 + i));
		}

		AuthorizeStats stats = authorizeAllConcurrently(token, paymentIds);

		assertThat(stats.http500).isZero();
		assertThat(stats.processorDeclined).isEqualTo(1);
		assertThat(stats.riskBlocked).isEqualTo(THREADS - 1);
		assertThat(countAssessmentsForMerchant(merchantId)).isEqualTo(THREADS);
		assertThat(countOutboxForMerchant(merchantId, "payment.failed")).isEqualTo(THREADS);
		assertThat(countOutboxForMerchant(merchantId, "payment.authorized")).isZero();
	}

	private AuthorizeStats authorizeAllConcurrently(String token, List<String> paymentIds) throws Exception {
		int n = paymentIds.size();
		ExecutorService pool = Executors.newFixedThreadPool(n);
		CountDownLatch ready = new CountDownLatch(n);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();
		for (String paymentId : paymentIds) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await(TIMEOUT_SEC, TimeUnit.SECONDS);
				return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
						.header("Authorization", "Bearer " + token))
						.andReturn();
			}));
		}
		assertThat(ready.await(TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		AtomicInteger authorized = new AtomicInteger();
		AtomicInteger riskBlocked = new AtomicInteger();
		AtomicInteger processorDeclined = new AtomicInteger();
		AtomicInteger http500 = new AtomicInteger();
		AtomicInteger other = new AtomicInteger();

		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(TIMEOUT_SEC, TimeUnit.SECONDS);
			int status = result.getResponse().getStatus();
			if (status == 500) {
				http500.incrementAndGet();
				continue;
			}
			if (status != 200) {
				other.incrementAndGet();
				continue;
			}
			JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
			String payStatus = body.get("status").asText();
			if ("AUTHORIZED".equals(payStatus)) {
				authorized.incrementAndGet();
			}
			else if ("FAILED".equals(payStatus)) {
				String code = body.get("failureCode").asText();
				if (PaymentService.FAILURE_RISK_BLOCKED.equals(code)) {
					riskBlocked.incrementAndGet();
				}
				else if (DeterministicSimulatedPaymentProcessor.FAILURE_SIMULATED_DECLINE.equals(code)) {
					processorDeclined.incrementAndGet();
				}
				else {
					other.incrementAndGet();
				}
			}
			else if ("CREATED".equals(payStatus)) {
				other.incrementAndGet(); // unexpected REVIEW in these scenarios
			}
			else {
				other.incrementAndGet();
			}
		}
		pool.shutdown();
		assertThat(pool.awaitTermination(TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
		assertThat(other.get()).as("unexpected outcomes").isZero();
		return new AuthorizeStats(authorized.get(), riskBlocked.get(), processorDeclined.get(), http500.get());
	}

	private record AuthorizeStats(int authorized, int riskBlocked, int processorDeclined, int http500) {
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

	private int countOutboxForMerchant(String merchantId, String eventType) {
		Integer c = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM outbox_events
						WHERE merchant_id = ? AND event_type = ?
						""",
				Integer.class, UUID.fromString(merchantId), eventType);
		return c == null ? 0 : c;
	}

	private int countLedgerForMerchant(String merchantId) {
		Integer c = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ledger_transactions WHERE merchant_id = ?",
				Integer.class, UUID.fromString(merchantId));
		return c == null ? 0 : c;
	}

	private String paymentStatus(String paymentId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId));
	}

	private void insertRule(UUID merchantId, String type, String params, String action, int priority) {
		jdbcTemplate.update(
				"""
						INSERT INTO risk_rules (
						  id, merchant_id, rule_type, parameters, action, priority, enabled)
						VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, TRUE)
						""",
				UUID.randomUUID(), merchantId, type, params, action, priority);
	}

	private String createPayment(String accessToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", "vel-key-" + UUID.randomUUID())
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
						{"name":"Velocity Conc Merchant","description":"Concurrency test"}
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
