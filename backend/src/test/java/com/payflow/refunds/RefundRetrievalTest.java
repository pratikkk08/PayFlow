package com.payflow.refunds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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

import com.payflow.TestcontainersConfiguration;
import com.payflow.refunds.processor.DeterministicSimulatedRefundProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefundRetrievalTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void ownerRetrievesRefundAndRejectsOtherCallers() throws Exception {
		String ownerToken = registerLogin("rget-owner-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("rget-other-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "rget-pay", 8000);
		String refundId = createRefund(ownerToken, paymentId, "rget-ok", 1500);

		mockMvc.perform(get("/api/v1/refunds/" + refundId)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refundId").value(refundId))
				.andExpect(jsonPath("$.paymentId").value(paymentId))
				.andExpect(jsonPath("$.merchantId").value(merchantId))
				.andExpect(jsonPath("$.merchantReference").value("rget-ok"))
				.andExpect(jsonPath("$.amountMinor").value(1500))
				.andExpect(jsonPath("$.currency").value("INR"))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.failureCode").value(nullValue()))
				.andExpect(jsonPath("$.completedAt").isNotEmpty())
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty())
				.andExpect(jsonPath("$.idempotencyKey").doesNotExist())
				.andExpect(jsonPath("$.requestFingerprint").doesNotExist());

		String declinedId = createRefund(ownerToken, paymentId,
				DeterministicSimulatedRefundProcessor.REF_PREFIX_DECLINE + "rget", 100);
		mockMvc.perform(get("/api/v1/refunds/" + declinedId)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureCode")
						.value(DeterministicSimulatedRefundProcessor.FAILURE_SIMULATED_DECLINE));

		mockMvc.perform(get("/api/v1/refunds/" + refundId)
				.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));

		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));

		mockMvc.perform(get("/api/v1/refunds/" + UUID.randomUUID())
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/refunds/" + refundId))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds"))
				.andExpect(status().isUnauthorized());

		String secret = createSecretKey(ownerToken, merchantId);
		mockMvc.perform(get("/api/v1/refunds/" + refundId)
				.header("Authorization", "Bearer " + secret))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + secret))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
	}

	@Test
	void historyIsPaginatedOrderedAndValidated() throws Exception {
		String ownerToken = registerLogin("rlist-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createCapturedPayment(ownerToken, merchantId, "rlist-pay", 20000);
		String emptyPaymentId = createCapturedPayment(ownerToken, merchantId, "rlist-empty", 500);

		String r1 = createRefund(ownerToken, paymentId, "rlist-a", 100);
		String r2 = createRefund(ownerToken, paymentId, "rlist-b", 200);
		String r3 = createRefund(ownerToken, paymentId, "rlist-c", 300);

		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content.length()").value(3))
				.andExpect(jsonPath("$.content[0].idempotencyKey").doesNotExist());

		Instant shared = Instant.parse("2026-02-01T00:00:00Z");
		jdbcTemplate.update("UPDATE refunds SET created_at = ? WHERE id IN (?, ?)",
				java.sql.Timestamp.from(shared), UUID.fromString(r1), UUID.fromString(r2));
		List<String> equalOrdered = jdbcTemplate.query(
				"""
						SELECT id::text FROM refunds
						WHERE id IN (?, ?)
						ORDER BY created_at DESC, id DESC
						""",
				(rs, rowNum) -> rs.getString(1),
				UUID.fromString(r1),
				UUID.fromString(r2));
		String firstEqual = equalOrdered.get(0);
		String secondEqual = equalOrdered.get(1);

		MvcResult listed = mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.param("page", "0")
				.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andReturn();
		JsonNode page0 = jsonMapper.readTree(listed.getResponse().getContentAsString()).get("content");
		assertThat(page0.get(0).get("refundId").asText()).isEqualTo(r3);
		assertThat(page0.get(1).get("refundId").asText()).isEqualTo(firstEqual);

		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.param("page", "1")
				.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].refundId").value(secondEqual));

		mockMvc.perform(get("/api/v1/payments/" + emptyPaymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.totalPages").value(0))
				.andExpect(jsonPath("$.content.length()").value(0));

		mockMvc.perform(get("/api/v1/payments/" + UUID.randomUUID() + "/refunds")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.param("page", "nope"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));
	}

	private String createRefund(String ownerToken, String paymentId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "rget-key-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantReference":"%s","amountMinor":%d}
						""".formatted(reference, amountMinor)))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonMapper.readTree(created.getResponse().getContentAsString()).get("refundId").asText();
	}

	private String createSecretKey(String ownerToken, String merchantId) throws Exception {
		MvcResult keyCreated = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Refund read key","type":"SECRET"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonMapper.readTree(keyCreated.getResponse().getContentAsString()).get("secret").asText();
	}

	private String createCapturedPayment(String ownerToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "pay-key-" + UUID.randomUUID())
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
						{"name":"Refund Retrieval Merchant","description":"Retrieval"}
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
