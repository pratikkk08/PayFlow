package com.payflow.ledger;

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
import com.payflow.refunds.processor.DeterministicSimulatedRefundProcessor;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LedgerPostingTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private TestLedgerPostingFaultInjector faultInjector;

	@Test
	void captureCreatesOneBalancedLedgerTransaction() throws Exception {
		String ownerToken = registerLogin("led-cap-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createAuthorizedPayment(ownerToken, merchantId, "ord-led-cap", 4200);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CAPTURED"));

		assertLedgerForSource("PAYMENT_CAPTURE", paymentId, 4200,
				"DEBIT", "PROCESSOR_CLEARING",
				"CREDIT", "MERCHANT_PAYABLE");
		assertTransactionCount("PAYMENT_CAPTURE", paymentId, 1);
	}

	@Test
	void successfulRefundCreatesReverseBalancedPosting() throws Exception {
		String ownerToken = registerLogin("led-ref-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-led-ref", 5000);

		MvcResult refund = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "led-ref-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-led-full", 5000)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andReturn();
		String refundId = jsonMapper.readTree(refund.getResponse().getContentAsString()).get("refundId").asText();

		assertLedgerForSource("REFUND", refundId, 5000,
				"DEBIT", "MERCHANT_PAYABLE",
				"CREDIT", "PROCESSOR_CLEARING");
		assertTransactionCount("PAYMENT_CAPTURE", paymentId, 1);
		assertTransactionCount("REFUND", refundId, 1);
	}

	@Test
	void partialRefundsCreateSeparateBalancedTransactionsWithinCapacity() throws Exception {
		String ownerToken = registerLogin("led-part-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-led-part", 10000);

		String refundA = createRefund(ownerToken, paymentId, "ref-part-a", 3000);
		String refundB = createRefund(ownerToken, paymentId, "ref-part-b", 4500);

		assertLedgerForSource("REFUND", refundA, 3000,
				"DEBIT", "MERCHANT_PAYABLE", "CREDIT", "PROCESSOR_CLEARING");
		assertLedgerForSource("REFUND", refundB, 4500,
				"DEBIT", "MERCHANT_PAYABLE", "CREDIT", "PROCESSOR_CLEARING");

		Integer refundTxns = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ledger_transactions WHERE source_type = 'REFUND' AND merchant_id = ?",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(refundTxns).isEqualTo(2);

		Long reserved = jdbcTemplate.queryForObject(
				"""
						SELECT COALESCE(SUM(amount_minor), 0) FROM refunds
						WHERE payment_id = ? AND status IN ('PENDING', 'SUCCEEDED')
						""",
				Long.class,
				UUID.fromString(paymentId));
		assertThat(reserved).isEqualTo(7500L);
	}

	@Test
	void idempotentRefundReplayDoesNotDuplicateLedgerPosting() throws Exception {
		String ownerToken = registerLogin("led-idem-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-led-idem", 3000);
		String key = "led-idem-key-" + UUID.randomUUID();
		String body = refundBody("ref-led-idem", 1000);

		MvcResult first = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		String refundId = jsonMapper.readTree(first.getResponse().getContentAsString()).get("refundId").asText();

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.refundId").value(refundId));

		assertTransactionCount("REFUND", refundId, 1);
		Integer entryCount = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM ledger_entries e
						JOIN ledger_transactions t ON t.id = e.ledger_transaction_id
						WHERE t.source_type = 'REFUND' AND t.source_id = ?
						""",
				Integer.class,
				UUID.fromString(refundId));
		assertThat(entryCount).isEqualTo(2);
	}

	@Test
	void concurrentCaptureAttemptsProduceAtMostOneLedgerPosting() throws Exception {
		String ownerToken = registerLogin("led-conc-cap-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createAuthorizedPayment(ownerToken, merchantId, "ord-led-conc-cap", 1800);

		int threads = 6;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
				return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
						.header("Authorization", "Bearer " + ownerToken))
						.andReturn();
			}));
		}
		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		int created = 0;
		int conflicts = 0;
		for (Future<MvcResult> future : futures) {
			int status = future.get(30, TimeUnit.SECONDS).getResponse().getStatus();
			if (status == 200) {
				created++;
			}
			else if (status == 409) {
				conflicts++;
			}
			else {
				throw new AssertionError("Unexpected status " + status);
			}
		}
		pool.shutdown();
		assertThat(created).isEqualTo(1);
		assertThat(conflicts).isEqualTo(threads - 1);
		assertTransactionCount("PAYMENT_CAPTURE", paymentId, 1);
	}

	@Test
	void concurrentRefundAttemptsPreserveCapacityAndLedger() throws Exception {
		String ownerToken = registerLogin("led-conc-rf-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-led-conc-rf", 1000);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			String key = "led-conc-rf-" + i + "-" + UUID.randomUUID();
			String ref = "ref-conc-rf-" + i;
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
				return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
						.header("Authorization", "Bearer " + ownerToken)
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content(refundBody(ref, 700)))
						.andReturn();
			}));
		}
		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		int created = 0;
		int conflicts = 0;
		for (Future<MvcResult> future : futures) {
			int status = future.get(30, TimeUnit.SECONDS).getResponse().getStatus();
			if (status == 201) {
				created++;
			}
			else if (status == 409) {
				conflicts++;
			}
			else {
				throw new AssertionError("Unexpected status " + status);
			}
		}
		pool.shutdown();
		assertThat(created).isEqualTo(1);
		assertThat(conflicts).isEqualTo(1);

		Integer refundTxns = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM ledger_transactions
						WHERE source_type = 'REFUND' AND merchant_id = ?
						""",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(refundTxns).isEqualTo(1);
		assertAllTransactionsBalanced(merchantId);
	}

	@Test
	void failedCaptureAndFailedRefundCreateNoLedgerPosting() throws Exception {
		String ownerToken = registerLogin("led-fail-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		String createdId = createPayment(ownerToken, merchantId, "ord-led-nocap", 900);
		mockMvc.perform(post("/api/v1/payments/" + createdId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isConflict());
		assertTransactionCount("PAYMENT_CAPTURE", createdId, 0);

		String failedAuthId = createPayment(ownerToken, merchantId,
				DeterministicSimulatedPaymentProcessor.REF_PREFIX_DECLINE + "led", 800);
		mockMvc.perform(post("/api/v1/payments/" + failedAuthId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"));
		assertTransactionCount("PAYMENT_CAPTURE", failedAuthId, 0);

		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-led-fail-ref", 2000);
		String declineRef = DeterministicSimulatedRefundProcessor.REF_PREFIX_DECLINE + "led";
		MvcResult declined = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "led-fail-ref-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody(declineRef, 500)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andReturn();
		String refundId = jsonMapper.readTree(declined.getResponse().getContentAsString()).get("refundId").asText();
		assertTransactionCount("REFUND", refundId, 0);
	}

	@Test
	void forcedLedgerFailureRollsBackCaptureAndRefund() throws Exception {
		String ownerToken = registerLogin("led-roll-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createAuthorizedPayment(ownerToken, merchantId, "ord-led-roll-cap", 1100);

		faultInjector.failNextPost();
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("ledger_posting_failed"));

		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId));
		assertThat(status).isEqualTo("AUTHORIZED");
		assertTransactionCount("PAYMENT_CAPTURE", paymentId, 0);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CAPTURED"));

		faultInjector.failNextPost();
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "led-roll-rf-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-led-roll", 400)))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("ledger_posting_failed"));

		Integer refunds = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refunds WHERE payment_id = ?",
				Integer.class,
				UUID.fromString(paymentId));
		assertThat(refunds).isZero();
		Integer refundTxns = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM ledger_transactions
						WHERE source_type = 'REFUND' AND merchant_id = ?
						""",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(refundTxns).isZero();
	}

	@Test
	void crossMerchantCannotCreateLedgerEntries() throws Exception {
		String ownerToken = registerLogin("led-own-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("led-other-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createAuthorizedPayment(ownerToken, merchantId, "ord-led-cross", 1300);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound());

		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId));
		assertThat(status).isEqualTo("AUTHORIZED");
		assertTransactionCount("PAYMENT_CAPTURE", paymentId, 0);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + otherToken)
				.header("Idempotency-Key", "led-cross-rf-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody("ref-cross", 100)))
				.andExpect(status().isNotFound());

		Integer refundTxns = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM ledger_transactions
						WHERE source_type = 'REFUND' AND merchant_id = ?
						""",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(refundTxns).isZero();
	}

	private void assertLedgerForSource(String sourceType, String sourceId, long amount,
			String firstDirection, String firstAccountType,
			String secondDirection, String secondAccountType) {
		Map<String, Object> txn = jdbcTemplate.queryForMap(
				"""
						SELECT id, currency, merchant_id FROM ledger_transactions
						WHERE source_type = ? AND source_id = ?
						""",
				sourceType, UUID.fromString(sourceId));
		UUID txnId = (UUID) txn.get("id");
		assertThat(txn.get("currency")).isEqualTo("INR");

		List<Map<String, Object>> entries = jdbcTemplate.queryForList(
				"""
						SELECT e.direction, e.amount_minor, a.account_type, a.merchant_id
						FROM ledger_entries e
						JOIN ledger_accounts a ON a.id = e.ledger_account_id
						WHERE e.ledger_transaction_id = ?
						ORDER BY e.direction
						""",
				txnId);
		assertThat(entries).hasSize(2);

		Long debits = jdbcTemplate.queryForObject(
				"""
						SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries
						WHERE ledger_transaction_id = ? AND direction = 'DEBIT'
						""",
				Long.class, txnId);
		Long credits = jdbcTemplate.queryForObject(
				"""
						SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries
						WHERE ledger_transaction_id = ? AND direction = 'CREDIT'
						""",
				Long.class, txnId);
		assertThat(debits).isEqualTo(credits).isEqualTo(amount);

		boolean sawFirst = entries.stream().anyMatch(e ->
				firstDirection.equals(e.get("direction"))
						&& firstAccountType.equals(e.get("account_type"))
						&& amount == ((Number) e.get("amount_minor")).longValue());
		boolean sawSecond = entries.stream().anyMatch(e ->
				secondDirection.equals(e.get("direction"))
						&& secondAccountType.equals(e.get("account_type"))
						&& amount == ((Number) e.get("amount_minor")).longValue());
		assertThat(sawFirst).isTrue();
		assertThat(sawSecond).isTrue();
	}

	private void assertTransactionCount(String sourceType, String sourceId, int expected) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ledger_transactions WHERE source_type = ? AND source_id = ?",
				Integer.class,
				sourceType,
				UUID.fromString(sourceId));
		assertThat(count).isEqualTo(expected);
	}

	private void assertAllTransactionsBalanced(String merchantId) {
		List<UUID> txnIds = jdbcTemplate.query(
				"SELECT id FROM ledger_transactions WHERE merchant_id = ?",
				(rs, rowNum) -> (UUID) rs.getObject("id"),
				UUID.fromString(merchantId));
		for (UUID txnId : txnIds) {
			Long debits = jdbcTemplate.queryForObject(
					"""
							SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries
							WHERE ledger_transaction_id = ? AND direction = 'DEBIT'
							""",
					Long.class, txnId);
			Long credits = jdbcTemplate.queryForObject(
					"""
							SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries
							WHERE ledger_transaction_id = ? AND direction = 'CREDIT'
							""",
					Long.class, txnId);
			assertThat(debits).isEqualTo(credits);
			assertThat(debits).isPositive();
		}
	}

	private String createRefund(String ownerToken, String paymentId, String reference, long amount)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "led-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(refundBody(reference, amount)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andReturn();
		return jsonMapper.readTree(result.getResponse().getContentAsString()).get("refundId").asText();
	}

	private static String refundBody(String reference, long amountMinor) {
		return """
				{"merchantReference":"%s","amountMinor":%d}
				""".formatted(reference, amountMinor);
	}

	private String createCapturedPayment(String ownerToken, String merchantId, String reference, long amount)
			throws Exception {
		String paymentId = createAuthorizedPayment(ownerToken, merchantId, reference, amount);
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CAPTURED"));
		return paymentId;
	}

	private String createAuthorizedPayment(String ownerToken, String merchantId, String reference, long amount)
			throws Exception {
		String paymentId = createPayment(ownerToken, merchantId, reference, amount);
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));
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
						{"name":"Ledger Merchant","description":"Ledger posting test"}
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
