package com.payflow.payments;

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
class PaymentIdempotencyTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void identicalRetryReplaysSamePaymentAndConflictOnPayloadChange() throws Exception {
		String ownerToken = registerLogin("idem-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String key = "idem-key-" + UUID.randomUUID();
		String body = payload(merchantId, "ref-replay", 1500);

		MvcResult first = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		String paymentId = jsonMapper.readTree(first.getResponse().getContentAsString()).get("paymentId").asText();

		MvcResult replay = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.paymentId").value(paymentId))
				.andExpect(jsonPath("$.amountMinor").value(1500))
				.andReturn();
		assertThat(replay.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload(merchantId, "ref-replay", 9999)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("idempotency_key_reuse"));

		Integer payments = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE merchant_id = ?",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(payments).isEqualTo(1);
	}

	@Test
	void missingAndInvalidIdempotencyKeyReturn400() throws Exception {
		String ownerToken = registerLogin("idem-bad-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String body = payload(merchantId, "ref-bad-key", 100);

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "short")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "bad key!!")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));
	}

	@Test
	void sameKeyIsIndependentPerMerchant() throws Exception {
		String sharedKey = "shared-merchant-key-1";
		String ownerA = registerLogin("idem-a-" + UUID.randomUUID() + "@example.com");
		String ownerB = registerLogin("idem-b-" + UUID.randomUUID() + "@example.com");
		String merchantA = createMerchant(ownerA);
		String merchantB = createMerchant(ownerB);

		MvcResult a = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerA)
				.header("Idempotency-Key", sharedKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload(merchantA, "ref-a", 200)))
				.andExpect(status().isCreated())
				.andReturn();
		MvcResult b = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerB)
				.header("Idempotency-Key", sharedKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload(merchantB, "ref-b", 300)))
				.andExpect(status().isCreated())
				.andReturn();

		String idA = jsonMapper.readTree(a.getResponse().getContentAsString()).get("paymentId").asText();
		String idB = jsonMapper.readTree(b.getResponse().getContentAsString()).get("paymentId").asText();
		assertThat(idA).isNotEqualTo(idB);
	}

	@Test
	void concurrentIdenticalRequestsCreateExactlyOnePayment() throws Exception {
		String ownerToken = registerLogin("idem-conc-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String key = "conc-ident-" + UUID.randomUUID();
		String body = payload(merchantId, "ref-conc-ident", 777);

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
				return mockMvc.perform(post("/api/v1/payments")
						.header("Authorization", "Bearer " + ownerToken)
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
						.andReturn();
			}));
		}

		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		List<String> paymentIds = new ArrayList<>();
		AtomicInteger created = new AtomicInteger();
		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(30, TimeUnit.SECONDS);
			assertThat(result.getResponse().getStatus()).isEqualTo(201);
			paymentIds.add(jsonMapper.readTree(result.getResponse().getContentAsString()).get("paymentId").asText());
			created.incrementAndGet();
		}
		pool.shutdown();
		assertThat(created.get()).isEqualTo(threads);
		assertThat(paymentIds.stream().distinct().count()).isEqualTo(1);

		Integer payments = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE merchant_id = ? AND merchant_reference = 'ref-conc-ident'",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(payments).isEqualTo(1);

		Integer completedKeys = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM payment_idempotency_records
						WHERE merchant_id = ? AND idempotency_key = ? AND status = 'COMPLETED'
						""",
				Integer.class,
				UUID.fromString(merchantId),
				key);
		assertThat(completedKeys).isEqualTo(1);
	}

	@Test
	void concurrentConflictingPayloadsCannotBothSucceed() throws Exception {
		String ownerToken = registerLogin("idem-conflict-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String key = "conc-conflict-" + UUID.randomUUID();

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			long amount = (i % 2 == 0) ? 1000L : 2000L;
			String body = payload(merchantId, "ref-conc-conflict", amount);
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
				return mockMvc.perform(post("/api/v1/payments")
						.header("Authorization", "Bearer " + ownerToken)
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
						.andReturn();
			}));
		}

		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		List<String> successPaymentIds = new ArrayList<>();
		List<Long> successAmounts = new ArrayList<>();
		int conflicts = 0;
		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(30, TimeUnit.SECONDS);
			int status = result.getResponse().getStatus();
			if (status == 201) {
				var body = jsonMapper.readTree(result.getResponse().getContentAsString());
				successPaymentIds.add(body.get("paymentId").asText());
				successAmounts.add(body.get("amountMinor").asLong());
			}
			else if (status == 409) {
				conflicts++;
			}
			else {
				throw new AssertionError("Unexpected status " + status);
			}
		}
		pool.shutdown();

		assertThat(successPaymentIds).isNotEmpty();
		assertThat(conflicts).isPositive();
		assertThat(successPaymentIds.stream().distinct().count()).isEqualTo(1);
		assertThat(successAmounts.stream().distinct().count()).isEqualTo(1);
		assertThat(successPaymentIds.size() + conflicts).isEqualTo(threads);

		Integer payments = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE merchant_id = ?",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(payments).isEqualTo(1);
	}

	@Test
	void failedDuplicateMerchantReferenceLeavesNoCompletedIdempotencyRecord() throws Exception {
		String ownerToken = registerLogin("idem-rollback-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "rollback-first-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload(merchantId, "ref-rollback", 400)))
				.andExpect(status().isCreated());

		String failingKey = "rollback-second-key";
		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", failingKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload(merchantId, "ref-rollback", 400)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("conflict"));

		Integer completed = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM payment_idempotency_records
						WHERE merchant_id = ? AND idempotency_key = ? AND status = 'COMPLETED'
						""",
				Integer.class,
				UUID.fromString(merchantId),
				failingKey);
		assertThat(completed).isZero();

		Integer any = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM payment_idempotency_records
						WHERE merchant_id = ? AND idempotency_key = ?
						""",
				Integer.class,
				UUID.fromString(merchantId),
				failingKey);
		assertThat(any).isZero();
	}

	private static String payload(String merchantId, String reference, long amountMinor) {
		return """
				{"merchantId":"%s","merchantReference":"%s","amountMinor":%d,"currency":"INR"}
				""".formatted(merchantId, reference, amountMinor);
	}

	private String createMerchant(String accessToken) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Idem Merchant","description":"Idempotency test"}
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
