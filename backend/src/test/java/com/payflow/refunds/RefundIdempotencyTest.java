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

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefundIdempotencyTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void identicalRetryReplaysSameRefundAndConflictOnAmountChange() throws Exception {
		String ownerToken = registerLogin("rf-idem-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-rf-replay", 5000);
		String key = "rf-idem-" + UUID.randomUUID();
		String body = refundBody("ref-replay", 1500);

		MvcResult first = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		String refundId = jsonMapper.readTree(first.getResponse().getContentAsString()).get("refundId").asText();

		MvcResult replay = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.refundId").value(refundId))
				.andExpect(jsonPath("$.amountMinor").value(1500))
				.andReturn();
		assertThat(replay.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-replay", 999)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("idempotency_key_reuse"));

		Integer refunds = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refunds WHERE payment_id = ?",
				Integer.class,
				UUID.fromString(paymentId));
		assertThat(refunds).isEqualTo(1);
	}

	@Test
	void sameKeyWithDifferentPaymentIdConflicts() throws Exception {
		String ownerToken = registerLogin("rf-payid-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentA = createCapturedPayment(ownerToken, merchantId, "ord-rf-a", 3000);
		String paymentB = createCapturedPayment(ownerToken, merchantId, "ord-rf-b", 3000);
		String key = "rf-shared-pay-" + UUID.randomUUID();

		mockMvc.perform(post("/api/v1/payments/" + paymentA + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-a", 500)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/payments/" + paymentB + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-b", 500)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("idempotency_key_reuse"));
	}

	@Test
	void missingAndInvalidIdempotencyKeyReturn400() throws Exception {
		String ownerToken = registerLogin("rf-badkey-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-rf-badkey", 1000);
		String body = refundBody("ref-bad", 100);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "short")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));
	}

	@Test
	void sameKeyIsIndependentPerMerchant() throws Exception {
		String sharedKey = "shared-refund-key-1";
		String ownerA = registerLogin("rf-a-" + UUID.randomUUID() + "@example.com");
		String ownerB = registerLogin("rf-b-" + UUID.randomUUID() + "@example.com");
		String merchantA = createMerchant(ownerA);
		String merchantB = createMerchant(ownerB);
		String paymentA = createCapturedPayment(ownerA, merchantA, "ord-a", 2000);
		String paymentB = createCapturedPayment(ownerB, merchantB, "ord-b", 2000);

		MvcResult a = mockMvc.perform(post("/api/v1/payments/" + paymentA + "/refunds")
				.header("Authorization", "Bearer " + ownerA)
				.header("Idempotency-Key", sharedKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-a", 200)))
				.andExpect(status().isCreated())
				.andReturn();
		MvcResult b = mockMvc.perform(post("/api/v1/payments/" + paymentB + "/refunds")
				.header("Authorization", "Bearer " + ownerB)
				.header("Idempotency-Key", sharedKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-b", 300)))
				.andExpect(status().isCreated())
				.andReturn();

		String idA = jsonMapper.readTree(a.getResponse().getContentAsString()).get("refundId").asText();
		String idB = jsonMapper.readTree(b.getResponse().getContentAsString()).get("refundId").asText();
		assertThat(idA).isNotEqualTo(idB);
	}

	@Test
	void paymentAndRefundCreateKeysAreIndependent() throws Exception {
		String ownerToken = registerLogin("rf-indep-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String sharedKey = "cross-op-key-" + UUID.randomUUID();

		MvcResult payment = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", sharedKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"ord-cross-op","amountMinor":2500,"currency":"INR"}
						""".formatted(merchantId)))
				.andExpect(status().isCreated())
				.andReturn();
		String paymentId = jsonMapper.readTree(payment.getResponse().getContentAsString()).get("paymentId").asText();

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", sharedKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-cross-op", 400)))
				.andExpect(status().isCreated());
	}

	@Test
	void concurrentIdenticalRequestsCreateExactlyOneRefund() throws Exception {
		String ownerToken = registerLogin("rf-conc-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-rf-conc", 8000);
		String key = "rf-conc-ident-" + UUID.randomUUID();
		String body = refundBody("ref-conc-ident", 1200);

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
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
		AtomicInteger created = new AtomicInteger();
		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(30, TimeUnit.SECONDS);
			assertThat(result.getResponse().getStatus()).isEqualTo(201);
			refundIds.add(jsonMapper.readTree(result.getResponse().getContentAsString()).get("refundId").asText());
			created.incrementAndGet();
		}
		pool.shutdown();
		assertThat(created.get()).isEqualTo(threads);
		assertThat(refundIds.stream().distinct().count()).isEqualTo(1);

		Integer refunds = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refunds WHERE payment_id = ? AND merchant_reference = 'ref-conc-ident'",
				Integer.class,
				UUID.fromString(paymentId));
		assertThat(refunds).isEqualTo(1);

		Integer completedKeys = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM refund_idempotency_records
						WHERE merchant_id = ? AND idempotency_key = ? AND status = 'COMPLETED'
						""",
				Integer.class,
				UUID.fromString(merchantId),
				key);
		assertThat(completedKeys).isEqualTo(1);
	}

	@Test
	void concurrentConflictingPayloadsCannotBothSucceed() throws Exception {
		String ownerToken = registerLogin("rf-conflict-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-rf-conflict", 5000);
		String key = "rf-conc-conflict-" + UUID.randomUUID();

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			long amount = (i % 2 == 0) ? 1000L : 2000L;
			String body = refundBody("ref-conc-conflict", amount);
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
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

		List<String> successRefundIds = new ArrayList<>();
		List<Long> successAmounts = new ArrayList<>();
		int conflicts = 0;
		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(30, TimeUnit.SECONDS);
			int status = result.getResponse().getStatus();
			if (status == 201) {
				var tree = jsonMapper.readTree(result.getResponse().getContentAsString());
				successRefundIds.add(tree.get("refundId").asText());
				successAmounts.add(tree.get("amountMinor").asLong());
			}
			else if (status == 409) {
				conflicts++;
			}
			else {
				throw new AssertionError("Unexpected status " + status);
			}
		}
		pool.shutdown();

		assertThat(successRefundIds).isNotEmpty();
		assertThat(conflicts).isPositive();
		assertThat(successRefundIds.stream().distinct().count()).isEqualTo(1);
		assertThat(successAmounts.stream().distinct().count()).isEqualTo(1);
		assertThat(successRefundIds.size() + conflicts).isEqualTo(threads);

		Integer refunds = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refunds WHERE payment_id = ?",
				Integer.class,
				UUID.fromString(paymentId));
		assertThat(refunds).isEqualTo(1);
	}

	@Test
	void differentKeysCannotExceedRefundableCapacity() throws Exception {
		String ownerToken = registerLogin("rf-cap-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-rf-cap", 1000);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "rf-cap-1-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-cap-1", 700)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "rf-cap-2-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-cap-2", 400)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("insufficient_refundable_capacity"));
	}

	@Test
	void failedDuplicateMerchantReferenceLeavesNoCompletedIdempotencyRecord() throws Exception {
		String ownerToken = registerLogin("rf-rollback-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-rf-rollback", 4000);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "rf-rollback-first")
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-rollback", 400)))
				.andExpect(status().isCreated());

		String failingKey = "rf-rollback-second";
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", failingKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-rollback", 400)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("conflict"));

		Integer completed = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM refund_idempotency_records
						WHERE merchant_id = ? AND idempotency_key = ? AND status = 'COMPLETED'
						""",
				Integer.class,
				UUID.fromString(merchantId),
				failingKey);
		assertThat(completed).isZero();

		Integer any = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM refund_idempotency_records
						WHERE merchant_id = ? AND idempotency_key = ?
						""",
				Integer.class,
				UUID.fromString(merchantId),
				failingKey);
		assertThat(any).isZero();
	}

	private static String refundBody(String reference, long amountMinor) {
		return """
				{"merchantReference":"%s","amountMinor":%d}
				""".formatted(reference, amountMinor);
	}

	private String createCapturedPayment(String ownerToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "pay-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"%s","amountMinor":%d,"currency":"INR"}
						""".formatted(merchantId, reference, amountMinor)))
				.andExpect(status().isCreated())
				.andReturn();
		String paymentId = jsonMapper.readTree(created.getResponse().getContentAsString()).get("paymentId").asText();
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());
		return paymentId;
	}

	private String createMerchant(String accessToken) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Refund Idem Merchant","description":"Refund idempotency test"}
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
