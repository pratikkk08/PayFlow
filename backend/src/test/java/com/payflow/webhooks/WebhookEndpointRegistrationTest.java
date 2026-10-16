package com.payflow.webhooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetAddress;
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
import com.payflow.webhooks.service.WebhookSecretCrypto;
import com.payflow.webhooks.service.WebhookSsrfGuard;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WebhookEndpointRegistrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private WebhookSecretCrypto webhookSecretCrypto;

	@Test
	void ownerRegistersListsUpdatesAndDeletesWebhookWithoutLeakingSecret() throws Exception {
		String ownerToken = registerLogin("wh-owner-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("wh-other-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String otherMerchantId = createMerchant(otherToken);

		MvcResult created = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "url":"https://hooks.example.com/payflow",
						  "eventTypes":["payment.created","payment.captured","refund.succeeded"],
						  "enabled":true
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.signingSecret").value(org.hamcrest.Matchers.startsWith("whsec_")))
				.andExpect(jsonPath("$.secretPrefix").isNotEmpty())
				.andExpect(jsonPath("$.url").value("https://hooks.example.com/payflow"))
				.andExpect(jsonPath("$.eventTypes[0]").value("payment.created"))
				.andReturn();

		JsonNode createdJson = jsonMapper.readTree(created.getResponse().getContentAsString());
		String webhookId = createdJson.get("id").asText();
		String signingSecret = createdJson.get("signingSecret").asText();

		String ciphertext = jdbcTemplate.queryForObject(
				"SELECT signing_secret_ciphertext FROM webhook_endpoints WHERE id = ?",
				String.class, UUID.fromString(webhookId));
		assertThat(ciphertext).isNotBlank().isNotEqualTo(signingSecret);
		assertThat(webhookSecretCrypto.decrypt(ciphertext)).isEqualTo(signingSecret);

		Integer rawInDb = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM webhook_endpoints WHERE signing_secret_ciphertext = ?",
				Integer.class, signingSecret);
		assertThat(rawInDb).isZero();

		mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(webhookId))
				.andExpect(jsonPath("$[0].signingSecret").doesNotExist())
				.andExpect(jsonPath("$[0].secretPrefix").isNotEmpty())
				.andExpect(jsonPath("$[0].url").value("https://hooks.example.com/payflow"));

		mockMvc.perform(patch("/api/v1/merchants/" + merchantId + "/webhooks/" + webhookId)
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "url":"https://hooks.example.com/payflow/v2",
						  "eventTypes":["payment.failed"],
						  "enabled":false
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://hooks.example.com/payflow/v2"))
				.andExpect(jsonPath("$.enabled").value(false))
				.andExpect(jsonPath("$.eventTypes[0]").value("payment.failed"))
				.andExpect(jsonPath("$.signingSecret").doesNotExist());

		mockMvc.perform(get("/api/v1/merchants/" + otherMerchantId + "/webhooks")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/v1/merchants/" + merchantId + "/webhooks/" + webhookId)
				.header("Authorization", "Bearer " + otherToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"enabled":true}
						"""))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/v1/merchants/" + merchantId + "/webhooks/" + webhookId)
				.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/v1/merchants/" + merchantId + "/webhooks/" + webhookId)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isNoContent());

		Integer remaining = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM webhook_endpoints WHERE id = ?",
				Integer.class, UUID.fromString(webhookId));
		assertThat(remaining).isZero();

		mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void unauthenticatedAndApiKeyRejected() throws Exception {
		String ownerToken = registerLogin("wh-auth-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String secretKey = createSecretApiKey(ownerToken, merchantId);

		mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/webhooks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"url":"https://hooks.example.com/x","eventTypes":["payment.created"]}
						"""))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + secretKey))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + secretKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"url":"https://hooks.example.com/x","eventTypes":["payment.created"]}
						"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectsInvalidUrlsAndEventSubscriptions() throws Exception {
		String ownerToken = registerLogin("wh-val-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		assertUrlRejected(ownerToken, merchantId, "http://hooks.example.com/x");
		assertUrlRejected(ownerToken, merchantId, "https://user:pass@hooks.example.com/x");
		assertUrlRejected(ownerToken, merchantId, "https://hooks.example.com/x#frag");
		assertUrlRejected(ownerToken, merchantId, "https://127.0.0.1/hooks");
		assertUrlRejected(ownerToken, merchantId, "https://10.0.0.5/hooks");
		assertUrlRejected(ownerToken, merchantId, "https://localhost/hooks");
		assertUrlRejected(ownerToken, merchantId, "https://metadata.google.internal/latest");
		assertUrlRejected(ownerToken, merchantId, "not-a-url");

		mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"url":"https://hooks.example.com/ok","eventTypes":["payment.unknown"]}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_event_types"));

		mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"url":"https://hooks.example.com/ok","eventTypes":[]}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void encryptionRoundTripAndSsrfAddressGuards() throws Exception {
		String plaintext = "whsec_roundtrip_secret_value";
		String cipher = webhookSecretCrypto.encrypt(plaintext);
		assertThat(cipher).isNotEqualTo(plaintext);
		assertThat(webhookSecretCrypto.decrypt(cipher)).isEqualTo(plaintext);

		assertThat(WebhookSsrfGuard.isBlockedHostname("localhost")).isTrue();
		assertThat(WebhookSsrfGuard.isBlockedHostname("metadata.google.internal")).isTrue();
		assertThat(WebhookSsrfGuard.isBlockedHostname("hooks.example.com")).isFalse();
		assertThat(WebhookSsrfGuard.isBlockedAddress(InetAddress.getByName("127.0.0.1"))).isTrue();
		assertThat(WebhookSsrfGuard.isBlockedAddress(InetAddress.getByName("10.1.2.3"))).isTrue();
		assertThat(WebhookSsrfGuard.isBlockedAddress(InetAddress.getByName("169.254.169.254"))).isTrue();
		assertThat(WebhookSsrfGuard.isBlockedAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
	}

	@Test
	void flywayWebhookEndpointsMigrationApplied() {
		Integer table = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.tables
						WHERE table_name = 'webhook_endpoints'
						""",
				Integer.class);
		assertThat(table).isEqualTo(1);

		Integer applied = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '15' AND success = TRUE",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	private void assertUrlRejected(String token, String merchantId, String url) throws Exception {
		mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/webhooks")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"url":"%s","eventTypes":["payment.created"]}
						""".formatted(url)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_webhook_url"));
	}

	private String createSecretApiKey(String token, String merchantId) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Webhook API key","type":"SECRET"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonMapper.readTree(created.getResponse().getContentAsString()).get("secret").asText();
	}

	private String createMerchant(String token) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Webhook Merchant"}
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
