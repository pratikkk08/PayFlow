package com.payflow.refunds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.payflow.refunds.processor.DeterministicSimulatedRefundProcessor;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefundConcurrencyTest {

	private static final int REQUEST_TIMEOUT_SECONDS = 30;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void concurrentOverlappingPartialRefundsAllowOnlyOneWinner() throws Exception {
		String ownerToken = registerLogin("rf-c700-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-c700", 1000);

		List<RefundAttempt> attempts = List.of(
				new RefundAttempt("key-700a-" + UUID.randomUUID(), "ref-700a", 700),
				new RefundAttempt("key-700b-" + UUID.randomUUID(), "ref-700b", 700));

		Outcome outcome = runConcurrent(ownerToken, paymentId, attempts);
		assertThat(outcome.created).isEqualTo(1);
		assertThat(outcome.conflicts).isEqualTo(1);
		assertThat(outcome.serverErrors).isZero();
		assertThat(outcome.deadlocks).isZero();
		assertReservedSum(paymentId, 700);
		assertNoNegativeRemaining(paymentId, 1000);
	}

	@Test
	void concurrentFittingPartialRefundsAllSucceedUpToExactCapturedAmount() throws Exception {
		String ownerToken = registerLogin("rf-cfit-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-cfit", 1000);

		List<RefundAttempt> attempts = List.of(
				new RefundAttempt("key-300a-" + UUID.randomUUID(), "ref-300a", 300),
				new RefundAttempt("key-400b-" + UUID.randomUUID(), "ref-400b", 400),
				new RefundAttempt("key-300c-" + UUID.randomUUID(), "ref-300c", 300));

		Outcome outcome = runConcurrent(ownerToken, paymentId, attempts);
		assertThat(outcome.created).isEqualTo(3);
		assertThat(outcome.conflicts).isZero();
		assertThat(outcome.serverErrors).isZero();
		assertThat(outcome.deadlocks).isZero();
		assertReservedSum(paymentId, 1000);
		assertRefundCount(paymentId, 3);
	}

	@Test
	void tenConcurrentRefundsOf200AllowExactlyFive() throws Exception {
		String ownerToken = registerLogin("rf-c10-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-c10", 1000);

		List<RefundAttempt> attempts = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			attempts.add(new RefundAttempt("key-200-" + i + "-" + UUID.randomUUID(), "ref-200-" + i, 200));
		}

		Outcome outcome = runConcurrent(ownerToken, paymentId, attempts);
		assertThat(outcome.created).isEqualTo(5);
		assertThat(outcome.conflicts).isEqualTo(5);
		assertThat(outcome.serverErrors).isZero();
		assertThat(outcome.deadlocks).isZero();
		assertReservedSum(paymentId, 1000);
		assertRefundCount(paymentId, 5);
		assertNoNegativeRemaining(paymentId, 1000);
	}

	@Test
	void concurrentIdenticalKeyRetriesCreateOneRefundAndReplayOriginal() throws Exception {
		String ownerToken = registerLogin("rf-cidem-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-cidem", 1000);
		String key = "identical-key-" + UUID.randomUUID();
		String body = refundBody("ref-identical", 400);

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
				return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
						.header("Authorization", "Bearer " + ownerToken)
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
						.andReturn();
			}));
		}

		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		List<String> refundIds = new ArrayList<>();
		List<String> bodies = new ArrayList<>();
		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			assertThat(result.getResponse().getStatus()).isEqualTo(201);
			String content = result.getResponse().getContentAsString();
			bodies.add(content);
			refundIds.add(jsonMapper.readTree(content).get("refundId").asText());
		}
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(refundIds.stream().distinct().count()).isEqualTo(1);
		assertThat(bodies.stream().distinct().count()).isEqualTo(1);
		assertRefundCount(paymentId, 1);
		assertReservedSum(paymentId, 400);
	}

	@Test
	void sameMerchantDifferentPaymentsTrackCapacityIndependently() throws Exception {
		String ownerToken = registerLogin("rf-cindep-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentA = createCapturedPayment(ownerToken, merchantId, "ord-indep-a", 1000);
		String paymentB = createCapturedPayment(ownerToken, merchantId, "ord-indep-b", 1000);

		List<RefundAttempt> attemptsA = List.of(
				new RefundAttempt("key-ia1-" + UUID.randomUUID(), "ref-ia1", 600),
				new RefundAttempt("key-ia2-" + UUID.randomUUID(), "ref-ia2", 600));
		List<RefundAttempt> attemptsB = List.of(
				new RefundAttempt("key-ib1-" + UUID.randomUUID(), "ref-ib1", 600),
				new RefundAttempt("key-ib2-" + UUID.randomUUID(), "ref-ib2", 600));

		ExecutorService pool = Executors.newFixedThreadPool(4);
		CountDownLatch ready = new CountDownLatch(4);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();

		for (RefundAttempt attempt : attemptsA) {
			futures.add(submitRefund(pool, ready, start, ownerToken, paymentA, attempt));
		}
		for (RefundAttempt attempt : attemptsB) {
			futures.add(submitRefund(pool, ready, start, ownerToken, paymentB, attempt));
		}

		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		int createdA = 0;
		int createdB = 0;
		int conflicts = 0;
		int serverErrors = 0;
		for (int i = 0; i < futures.size(); i++) {
			MvcResult result = futures.get(i).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			int status = result.getResponse().getStatus();
			if (status == 201) {
				if (i < 2) {
					createdA++;
				}
				else {
					createdB++;
				}
			}
			else if (status == 409) {
				conflicts++;
			}
			else if (status >= 500) {
				serverErrors++;
			}
		}
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(createdA).isEqualTo(1);
		assertThat(createdB).isEqualTo(1);
		assertThat(conflicts).isEqualTo(2);
		assertThat(serverErrors).isZero();
		assertReservedSum(paymentA, 600);
		assertReservedSum(paymentB, 600);
	}

	@Test
	void concurrentRefundsByDifferentMerchantsRemainIsolated() throws Exception {
		String ownerA = registerLogin("rf-cma-" + UUID.randomUUID() + "@example.com");
		String ownerB = registerLogin("rf-cmb-" + UUID.randomUUID() + "@example.com");
		String merchantA = createMerchant(ownerA);
		String merchantB = createMerchant(ownerB);
		String paymentA = createCapturedPayment(ownerA, merchantA, "ord-ma", 1000);
		String paymentB = createCapturedPayment(ownerB, merchantB, "ord-mb", 1000);

		ExecutorService pool = Executors.newFixedThreadPool(4);
		CountDownLatch ready = new CountDownLatch(4);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();

		futures.add(submitRefund(pool, ready, start, ownerA, paymentA,
				new RefundAttempt("key-ma1-" + UUID.randomUUID(), "ref-ma1", 700)));
		futures.add(submitRefund(pool, ready, start, ownerA, paymentA,
				new RefundAttempt("key-ma2-" + UUID.randomUUID(), "ref-ma2", 700)));
		futures.add(submitRefund(pool, ready, start, ownerB, paymentB,
				new RefundAttempt("key-mb1-" + UUID.randomUUID(), "ref-mb1", 700)));
		futures.add(submitRefund(pool, ready, start, ownerB, paymentB,
				new RefundAttempt("key-mb2-" + UUID.randomUUID(), "ref-mb2", 700)));

		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		int created = 0;
		int conflicts = 0;
		int serverErrors = 0;
		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			int status = result.getResponse().getStatus();
			if (status == 201) {
				created++;
			}
			else if (status == 409) {
				conflicts++;
			}
			else if (status >= 500) {
				serverErrors++;
			}
		}
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(created).isEqualTo(2);
		assertThat(conflicts).isEqualTo(2);
		assertThat(serverErrors).isZero();
		assertReservedSum(paymentA, 700);
		assertReservedSum(paymentB, 700);
	}

	@Test
	void concurrentRefundsOnNonCapturedPaymentAreRejected() throws Exception {
		String ownerToken = registerLogin("rf-cuncap-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId, "ord-uncap", 1000);
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));

		List<RefundAttempt> attempts = List.of(
				new RefundAttempt("key-u1-" + UUID.randomUUID(), "ref-u1", 200),
				new RefundAttempt("key-u2-" + UUID.randomUUID(), "ref-u2", 200),
				new RefundAttempt("key-u3-" + UUID.randomUUID(), "ref-u3", 200));

		Outcome outcome = runConcurrent(ownerToken, paymentId, attempts);
		assertThat(outcome.created).isZero();
		assertThat(outcome.conflicts).isEqualTo(3);
		assertThat(outcome.serverErrors).isZero();
		assertRefundCount(paymentId, 0);
		assertReservedSum(paymentId, 0);
	}

	@Test
	void declinedRefundLeavesNoCapacityReservation() throws Exception {
		String ownerToken = registerLogin("rf-cfail-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-fail", 1000);

		String declineRef = DeterministicSimulatedRefundProcessor.REF_PREFIX_DECLINE + "x";
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "key-decline-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody(declineRef, 500)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FAILED"));

		assertReservedSum(paymentId, 0);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "key-after-decline-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-after-decline", 1000)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"));

		assertReservedSum(paymentId, 1000);
	}

	@Test
	void capacityConflictRollsBackIdempotencyReservation() throws Exception {
		String ownerToken = registerLogin("rf-croll-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-roll", 1000);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "key-first-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-first", 800)))
				.andExpect(status().isCreated());

		String failingKey = "key-over-" + UUID.randomUUID();
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", failingKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-over", 300)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("insufficient_refundable_capacity"));

		Integer any = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM refund_idempotency_records
						WHERE merchant_id = ? AND idempotency_key = ?
						""",
				Integer.class,
				UUID.fromString(merchantId),
				failingKey);
		assertThat(any).isZero();
		assertReservedSum(paymentId, 800);
	}

	private Outcome runConcurrent(String ownerToken, String paymentId, List<RefundAttempt> attempts)
			throws Exception {
		int threads = attempts.size();
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();

		for (RefundAttempt attempt : attempts) {
			futures.add(submitRefund(pool, ready, start, ownerToken, paymentId, attempt));
		}

		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		AtomicInteger created = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		AtomicInteger serverErrors = new AtomicInteger();
		AtomicInteger deadlocks = new AtomicInteger();

		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			int status = result.getResponse().getStatus();
			String body = result.getResponse().getContentAsString();
			if (status == 201) {
				created.incrementAndGet();
			}
			else if (status == 409) {
				conflicts.incrementAndGet();
			}
			else if (status >= 500) {
				serverErrors.incrementAndGet();
				if (body != null && body.toLowerCase().contains("deadlock")) {
					deadlocks.incrementAndGet();
				}
			}
			else {
				throw new AssertionError("Unexpected status " + status + " body=" + body);
			}
		}
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		return new Outcome(created.get(), conflicts.get(), serverErrors.get(), deadlocks.get());
	}

	private Future<MvcResult> submitRefund(ExecutorService pool, CountDownLatch ready, CountDownLatch start,
			String ownerToken, String paymentId, RefundAttempt attempt) {
		return pool.submit(() -> {
			ready.countDown();
			assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
			return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
					.header("Authorization", "Bearer " + ownerToken)
					.header("Idempotency-Key", attempt.key())
					.contentType(MediaType.APPLICATION_JSON)
					.content(refundBody(attempt.reference(), attempt.amountMinor())))
					.andReturn();
		});
	}

	private void assertReservedSum(String paymentId, long expected) {
		Long reserved = jdbcTemplate.queryForObject(
				"""
						SELECT COALESCE(SUM(amount_minor), 0) FROM refunds
						WHERE payment_id = ? AND status IN ('PENDING', 'SUCCEEDED')
						""",
				Long.class,
				UUID.fromString(paymentId));
		assertThat(reserved).isEqualTo(expected);
	}

	private void assertRefundCount(String paymentId, int expected) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refunds WHERE payment_id = ?",
				Integer.class,
				UUID.fromString(paymentId));
		assertThat(count).isEqualTo(expected);
	}

	private void assertNoNegativeRemaining(String paymentId, long capturedAmount) {
		Long reserved = jdbcTemplate.queryForObject(
				"""
						SELECT COALESCE(SUM(amount_minor), 0) FROM refunds
						WHERE payment_id = ? AND status IN ('PENDING', 'SUCCEEDED')
						""",
				Long.class,
				UUID.fromString(paymentId));
		assertThat(capturedAmount - reserved).isGreaterThanOrEqualTo(0);
		assertThat(reserved).isLessThanOrEqualTo(capturedAmount);
	}

	private static String refundBody(String reference, long amountMinor) {
		return """
				{"merchantReference":"%s","amountMinor":%d}
				""".formatted(reference, amountMinor);
	}

	private String createCapturedPayment(String ownerToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		String paymentId = createPayment(ownerToken, merchantId, reference, amountMinor);
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CAPTURED"));
		return paymentId;
	}

	private String createPayment(String accessToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", "pay-" + UUID.randomUUID())
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
						{"name":"Refund Concurrency Merchant","description":"Concurrency stress"}
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

	private record RefundAttempt(String key, String reference, long amountMinor) {
	}

	private record Outcome(int created, int conflicts, int serverErrors, int deadlocks) {
	}
}
