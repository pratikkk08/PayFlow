package com.payflow.payments;

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

import com.payflow.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PaymentCreationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createsPaymentAsOwnerWithCreatedStatus() throws Exception {
		String ownerToken = registerLogin("pay-owner-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "create-ord-1001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "merchantId":"%s",
						  "merchantReference":"ord-1001",
						  "amountMinor":2599,
						  "currency":"INR",
						  "description":"Test charge"
						}
						""".formatted(merchantId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.paymentId").isNotEmpty())
				.andExpect(jsonPath("$.merchantId").value(merchantId))
				.andExpect(jsonPath("$.merchantReference").value("ord-1001"))
				.andExpect(jsonPath("$.amountMinor").value(2599))
				.andExpect(jsonPath("$.currency").value("INR"))
				.andExpect(jsonPath("$.status").value("CREATED"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.version").doesNotExist())
				.andReturn();

		JsonNode body = jsonMapper.readTree(created.getResponse().getContentAsString());
		String paymentId = body.get("paymentId").asText();

		var row = jdbcTemplate.queryForMap("SELECT * FROM payments WHERE id = ?", UUID.fromString(paymentId));
		assertThat(row.get("merchant_id").toString()).isEqualTo(merchantId);
		assertThat(row.get("merchant_reference")).isEqualTo("ord-1001");
		assertThat(((Number) row.get("amount_minor")).longValue()).isEqualTo(2599L);
		assertThat(row.get("currency")).isEqualTo("INR");
		assertThat(row.get("status")).isEqualTo("CREATED");
		assertThat(row.get("description")).isEqualTo("Test charge");
	}

	@Test
	void rejectsInvalidAmountsAndUnsupportedCurrency() throws Exception {
		String ownerToken = registerLogin("pay-valid-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "create-ord-zero")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"ord-zero","amountMinor":0,"currency":"INR"}
						""".formatted(merchantId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "create-ord-neg")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"ord-neg","amountMinor":-5,"currency":"INR"}
						""".formatted(merchantId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "create-ord-usd")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"ord-usd","amountMinor":100,"currency":"USD"}
						""".formatted(merchantId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"));
	}

	@Test
	void rejectsUnauthenticatedAndCrossMerchantAccess() throws Exception {
		String ownerToken = registerLogin("pay-owner2-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("pay-other-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		mockMvc.perform(post("/api/v1/payments")
				.header("Idempotency-Key", "create-ord-anon")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"ord-anon","amountMinor":100,"currency":"INR"}
						""".formatted(merchantId)))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + otherToken)
				.header("Idempotency-Key", "create-ord-cross")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"ord-cross","amountMinor":100,"currency":"INR"}
						""".formatted(merchantId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE merchant_id = ? AND merchant_reference = 'ord-cross'",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(count).isZero();
	}

	@Test
	void rejectsDuplicateMerchantReferenceAndApiKeyAuth() throws Exception {
		String ownerToken = registerLogin("pay-dup-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		String payload = """
				{"merchantId":"%s","merchantReference":"ord-dup","amountMinor":500,"currency":"INR"}
				""".formatted(merchantId);

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "create-ord-dup-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + ownerToken)
				.header("Idempotency-Key", "create-ord-dup-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("conflict"));

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE merchant_id = ? AND merchant_reference = 'ord-dup'",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(count).isEqualTo(1);

		MvcResult keyCreated = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Server key","type":"SECRET"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		String secret = jsonMapper.readTree(keyCreated.getResponse().getContentAsString()).get("secret").asText();

		mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + secret)
				.header("Idempotency-Key", "create-ord-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"merchantId":"%s","merchantReference":"ord-key","amountMinor":100,"currency":"INR"}
						""".formatted(merchantId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("forbidden"));
	}

	private String createMerchant(String accessToken) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Pay Merchant","description":"Payments test"}
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
