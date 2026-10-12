package com.payflow.merchants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class MerchantApiKeyTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void merchantOwnershipAndApiKeyLifecycle() throws Exception {
		String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
		String otherEmail = "other-" + UUID.randomUUID() + "@example.com";
		String password = "Str0ngPass!";

		String ownerToken = registerLogin(ownerEmail, password);
		String otherToken = registerLogin(otherEmail, password);

		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Acme Payments","description":"Test merchant"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerUserId").isNotEmpty())
				.andReturn();
		String merchantId = jsonMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/api/v1/merchants/" + merchantId)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Acme Payments"));

		mockMvc.perform(get("/api/v1/merchants/" + merchantId)
				.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/v1/merchants/" + merchantId)
				.header("Authorization", "Bearer " + otherToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Hijacked"}
						"""))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + otherToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Server key","type":"SECRET"}
						"""))
				.andExpect(status().isNotFound());

		MvcResult keyCreated = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Server key","type":"SECRET"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("sk_test_")))
				.andReturn();
		JsonNode keyJson = jsonMapper.readTree(keyCreated.getResponse().getContentAsString());
		String secret = keyJson.get("secret").asText();
		String keyId = keyJson.get("id").asText();

		Integer rawStored = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM api_keys WHERE secret_hash = ?",
				Integer.class,
				secret);
		assertThat(rawStored).isZero();

		mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].secret").doesNotExist())
				.andExpect(jsonPath("$[0].keyPrefix").isNotEmpty());

		mockMvc.perform(get("/api/v1/integrations/me")
				.header("Authorization", "Bearer " + secret))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.merchantId").value(merchantId))
				.andExpect(jsonPath("$.apiKeyId").value(keyId));

		mockMvc.perform(get("/api/v1/users/me")
				.header("Authorization", "Bearer " + secret))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/integrations/me")
				.header("Authorization", "Bearer sk_test_invalid"))
				.andExpect(status().isUnauthorized());

		MvcResult pub = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + ownerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Browser key","type":"PUBLISHABLE"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		String publishable = jsonMapper.readTree(pub.getResponse().getContentAsString()).get("secret").asText();
		mockMvc.perform(get("/api/v1/integrations/me")
				.header("Authorization", "Bearer " + publishable))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/api/v1/merchants/" + merchantId + "/api-keys/" + keyId)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/merchants/" + merchantId + "/api-keys/" + keyId)
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/integrations/me")
				.header("Authorization", "Bearer " + secret))
				.andExpect(status().isUnauthorized());
	}

	private String registerLogin(String email, String password) throws Exception {
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
