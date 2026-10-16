package com.payflow.refunds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.payflow.TestcontainersConfiguration;
import com.payflow.payments.processor.DeterministicSimulatedPaymentProcessor;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefundCreationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void fullRefundSucceedsWithCorrectPersistedState() throws Exception {
		String ownerToken = registerLogin("refund-full-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-refund-full", 5000);

		MvcResult created = mockMvc.perform(refundPost(paymentId, ownerToken, newKey(),
				"""
						{"merchantReference":"ref-full-1","amountMinor":5000,"reason":"customer request"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.paymentId").value(paymentId))
				.andExpect(jsonPath("$.merchantId").value(merchantId))
				.andExpect(jsonPath("$.merchantReference").value("ref-full-1"))
				.andExpect(jsonPath("$.amountMinor").value(5000))
				.andExpect(jsonPath("$.currency").value("INR"))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.completedAt").isNotEmpty())
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty())
				.andExpect(jsonPath("$.refundId").isNotEmpty())
				.andReturn();

		UUID refundId = UUID.fromString(
				jsonMapper.readTree(created.getResponse().getContentAsString()).get("refundId").asText());
		var row = jdbcTemplate.queryForMap(
				"""
						SELECT amount_minor, currency, status, completed_at, created_at, updated_at, payment_id, merchant_id
						FROM refunds WHERE id = ?
						""",
				refundId);
		assertThat(row.get("amount_minor")).isEqualTo(5000L);
		assertThat(row.get("currency")).isEqualTo("INR");
		assertThat(row.get("status")).isEqualTo("SUCCEEDED");
		assertThat(row.get("completed_at")).isNotNull();
		assertThat(row.get("created_at")).isNotNull();
		assertThat(row.get("updated_at")).isNotNull();
		assertThat(row.get("payment_id").toString()).isEqualTo(paymentId);
		assertThat(row.get("merchant_id").toString()).isEqualTo(merchantId);

		String paymentStatus = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, UUID.fromString(paymentId));
		assertThat(paymentStatus).isEqualTo("CAPTURED");
	}

	@Test
	void partialAndMultipleRefundsUpToExactCapturedAmountSucceed() throws Exception {
		String ownerToken = registerLogin("refund-partial-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-refund-partial", 10000);

		mockMvc.perform(refundPost(paymentId, ownerToken, newKey(),
				"""
						{"merchantReference":"ref-p1","amountMinor":3000}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.amountMinor").value(3000));

		mockMvc.perform(refundPost(paymentId, ownerToken, newKey(),
				"""
						{"merchantReference":"ref-p2","amountMinor":4500}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.amountMinor").value(4500));

		mockMvc.perform(refundPost(paymentId, ownerToken, newKey(),
				"""
						{"merchantReference":"ref-p3","amountMinor":2500}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.amountMinor").value(2500));

		Long reserved = jdbcTemplate.queryForObject(
				"""
						SELECT COALESCE(SUM(amount_minor), 0) FROM refunds
						WHERE payment_id = ? AND status IN ('PENDING', 'SUCCEEDED')
						""",
				Long.class,
				UUID.fromString(paymentId));
		assertThat(reserved).isEqualTo(10000L);
	}

	@Test
	void exceedingCapacityReturns409() throws Exception {
		String ownerToken = registerLogin("refund-over-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-refund-over", 2000);

		mockMvc.perform(refundPost(paymentId, ownerToken, newKey(),
				"""
						{"merchantReference":"ref-over-1","amountMinor":1500}
						"""))
				.andExpect(status().isCreated());

		mockMvc.perform(refundPost(paymentId, ownerToken, newKey(),
				"""
						{"merchantReference":"ref-over-2","amountMinor":600}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("insufficient_refundable_capacity"));

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refunds WHERE payment_id = ?",
				Integer.class,
				UUID.fromString(paymentId));
		assertThat(count).isEqualTo(1);
	}

	@Test
	void refundsOnNonCapturedPaymentsReturn409() throws Exception {
		String ownerToken = registerLogin("refund-state-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		String createdId = createPayment(ownerToken, merchantId, "ord-rf-created", 1000);
		expectNotCaptured(ownerToken, createdId, "ref-created");

		String authorizedId = createPayment(ownerToken, merchantId, "ord-rf-auth", 1100);
		authorize(ownerToken, authorizedId);
		expectNotCaptured(ownerToken, authorizedId, "ref-auth");

		String failedId = createPayment(ownerToken, merchantId,
				DeterministicSimulatedPaymentProcessor.REF_PREFIX_DECLINE + "rf", 1200);
		mockMvc.perform(post("/api/v1/payments/" + failedId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"));
		expectNotCaptured(ownerToken, failedId, "ref-failed");

		String cancelledId = createPayment(ownerToken, merchantId, "ord-rf-cancel", 1300);
		mockMvc.perform(post("/api/v1/payments/" + cancelledId + "/cancel")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());
		expectNotCaptured(ownerToken, cancelledId, "ref-cancel");
	}

	@Test
	void zeroOrNegativeAmountReturns400() throws Exception {
		String ownerToken = registerLogin("refund-amt-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-refund-amt", 900);

		mockMvc.perform(refundPost(paymentId, ownerToken, newKey(),
				"""
						{"merchantReference":"ref-zero","amountMinor":0}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(refundPost(paymentId, ownerToken, newKey(),
				"""
						{"merchantReference":"ref-neg","amountMinor":-1}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));
	}

	@Test
	void duplicateRefundReferenceWithDifferentKeyReturns409WithoutExtraRow() throws Exception {
		String ownerToken = registerLogin("refund-dup-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-refund-dup", 4000);

		String payload = """
				{"merchantReference":"ref-dup","amountMinor":500}
				""";
		mockMvc.perform(refundPost(paymentId, ownerToken, newKey(), payload))
				.andExpect(status().isCreated());

		mockMvc.perform(refundPost(paymentId, ownerToken, newKey(), payload))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("conflict"));

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refunds WHERE merchant_id = ? AND merchant_reference = 'ref-dup'",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(count).isEqualTo(1);
	}

	@Test
	void unauthenticatedCrossMerchantAndApiKeyAreRejected() throws Exception {
		String ownerToken = registerLogin("refund-sec-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("refund-other-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "ord-refund-sec", 2500);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantReference":"ref-unauth","amountMinor":100}
						"""))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(refundPost(paymentId, otherToken, newKey(),
				"""
						{"merchantReference":"ref-cross","amountMinor":100}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));

		MvcResult keyCreated = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Refund key","type":"SECRET"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		String secret = jsonMapper.readTree(keyCreated.getResponse().getContentAsString()).get("secret").asText();

		mockMvc.perform(refundPost(paymentId, secret, newKey(),
				"""
						{"merchantReference":"ref-apikey","amountMinor":100}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
	}

	private void expectNotCaptured(String ownerToken, String paymentId, String reference) throws Exception {
		mockMvc.perform(refundPost(paymentId, ownerToken, newKey(),
				"""
						{"merchantReference":"%s","amountMinor":100}
						""".formatted(reference)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("payment_not_captured"));
	}

	private MockHttpServletRequestBuilder refundPost(String paymentId, String bearer, String idempotencyKey,
			String body) {
		return post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + bearer)
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	private static String newKey() {
		return "rf-key-" + UUID.randomUUID();
	}

	private String createCapturedPayment(String ownerToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		String paymentId = createPayment(ownerToken, merchantId, reference, amountMinor);
		authorize(ownerToken, paymentId);
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CAPTURED"));
		return paymentId;
	}

	private void authorize(String ownerToken, String paymentId) throws Exception {
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));
	}

	private String createPayment(String accessToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", "pay-key-" + UUID.randomUUID())
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
						{"name":"Refund Merchant","description":"Refund API test"}
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
