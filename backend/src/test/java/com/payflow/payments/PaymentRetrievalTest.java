package com.payflow.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PaymentRetrievalTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void getOwnedPaymentAndRejectInaccessibleOrUnauthenticated() throws Exception {
		String ownerToken = registerLogin("ret-owner-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("ret-other-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId, "ret-get-1", 1500);

		mockMvc.perform(get("/api/v1/payments/" + paymentId)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paymentId").value(paymentId))
				.andExpect(jsonPath("$.merchantId").value(merchantId))
				.andExpect(jsonPath("$.status").value("CREATED"))
				.andExpect(jsonPath("$.amountMinor").value(1500));

		mockMvc.perform(get("/api/v1/payments/" + paymentId)
				.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));

		mockMvc.perform(get("/api/v1/payments/" + paymentId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void apiKeyCannotAccessPaymentRetrieval() throws Exception {
		String ownerToken = registerLogin("ret-key-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId, "ret-key-1", 1600);

		MvcResult keyCreated = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Server key","type":"SECRET"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		String secret = jsonMapper.readTree(keyCreated.getResponse().getContentAsString()).get("secret").asText();

		mockMvc.perform(get("/api/v1/payments/" + paymentId)
				.header("Authorization", "Bearer " + secret))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + secret)
				.param("merchantId", merchantId))
				.andExpect(status().isForbidden());
	}

	@Test
	void listFiltersPaginationOrderingAndValidation() throws Exception {
		String ownerToken = registerLogin("ret-list-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("ret-list-o-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String otherMerchantId = createMerchant(otherToken);

		String p1 = createPayment(ownerToken, merchantId, "ret-list-a", 1000);
		String p2 = createPayment(ownerToken, merchantId, "ret-list-b", 1100);
		String p3 = createPayment(ownerToken, merchantId, "ret-list-c", 1200);
		createPayment(otherToken, otherMerchantId, "ret-list-other", 1300);

		mockMvc.perform(post("/api/v1/payments/" + p2 + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/payments/" + p3 + "/cancel")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());

		Instant shared = Instant.parse("2026-01-15T12:00:00Z");
		jdbcTemplate.update("UPDATE payments SET created_at = ? WHERE id IN (?, ?)",
				java.sql.Timestamp.from(shared), UUID.fromString(p1), UUID.fromString(p2));
		// Postgres UUID order (not Java UUID.compareTo) is the stable tie-breaker.
		java.util.List<String> equalOrdered = jdbcTemplate.query(
				"""
						SELECT id::text FROM payments
						WHERE id IN (?, ?)
						ORDER BY created_at DESC, id DESC
						""",
				(rs, rowNum) -> rs.getString(1),
				UUID.fromString(p1),
				UUID.fromString(p2));
		String firstEqual = equalOrdered.get(0);
		String secondEqual = equalOrdered.get(1);

		MvcResult listed = mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.param("merchantId", merchantId)
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
		assertThat(page0.get(0).get("paymentId").asText()).isEqualTo(p3);
		assertThat(page0.get(1).get("paymentId").asText()).isEqualTo(firstEqual);

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.param("merchantId", merchantId)
				.param("page", "1")
				.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].paymentId").value(secondEqual));

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.param("merchantId", merchantId)
				.param("status", "AUTHORIZED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].paymentId").value(p2))
				.andExpect(jsonPath("$.content[0].status").value("AUTHORIZED"));

		Instant from = Instant.parse("2026-01-01T00:00:00Z");
		Instant to = Instant.parse("2026-01-20T00:00:00Z");
		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.param("merchantId", merchantId)
				.param("createdFrom", from.toString())
				.param("createdTo", to.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.param("merchantId", merchantId)
				.param("status", "CAPTURED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + otherToken)
				.param("merchantId", merchantId))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.param("merchantId", merchantId)
				.param("status", "NOPE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.param("merchantId", merchantId)
				.param("page", "-1"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.param("merchantId", merchantId)
				.param("size", "101"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.param("merchantId", merchantId)
				.param("createdFrom", to.toString())
				.param("createdTo", from.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));
	}

	private String createPayment(String accessToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", "ret-key-" + UUID.randomUUID())
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
						{"name":"Retrieval Merchant","description":"Retrieval test"}
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
