package com.payflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.payflow.TestcontainersConfiguration;
import com.payflow.shared.security.PayflowSecurityProperties;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BrowserSessionSecurityTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private PayflowSecurityProperties securityProperties;

	@Test
	void browserLoginSetsHttpOnlyRefreshCookieWithoutBodyRefresh() throws Exception {
		String email = "browser-" + UUID.randomUUID() + "@example.com";
		String password = "Str0ngPass!";
		register(email, password);

		String csrf = bootstrapCsrf();
		MvcResult login = mockMvc.perform(post("/api/v1/auth/browser/login")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-XSRF-TOKEN", csrf)
				.cookie(new Cookie("XSRF-TOKEN", csrf))
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").doesNotExist())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(cookie().exists(refreshCookieName()))
				.andExpect(cookie().httpOnly(refreshCookieName(), true))
				.andExpect(cookie().secure(refreshCookieName(), false))
				.andExpect(cookie().path(refreshCookieName(),
						securityProperties.getBrowserSession().getRefreshCookiePath()))
				.andReturn();

		Cookie refresh = login.getResponse().getCookie(refreshCookieName());
		assertThat(refresh).isNotNull();
		assertThat(refresh.getValue()).isNotBlank();
		assertThat(refresh.getAttribute("SameSite")).isEqualToIgnoringCase("Lax");

		JsonNode body = jsonMapper.readTree(login.getResponse().getContentAsString());
		String access = body.get("accessToken").asText();

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void browserLoginWithoutCsrfIsForbidden() throws Exception {
		String email = "csrf-" + UUID.randomUUID() + "@example.com";
		register(email, "Str0ngPass!");

		mockMvc.perform(post("/api/v1/auth/browser/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"Str0ngPass!"}
						""".formatted(email)))
				.andExpect(status().isForbidden());
	}

	@Test
	void browserRefreshRotatesCookieAndJsonApisRemainAvailable() throws Exception {
		String email = "refresh-" + UUID.randomUUID() + "@example.com";
		String password = "Str0ngPass!";
		register(email, password);

		String csrf = bootstrapCsrf();
		MvcResult login = mockMvc.perform(post("/api/v1/auth/browser/login")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-XSRF-TOKEN", csrf)
				.cookie(new Cookie("XSRF-TOKEN", csrf))
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		Cookie refresh1 = login.getResponse().getCookie(refreshCookieName());
		assertThat(refresh1).isNotNull();

		csrf = bootstrapCsrf();
		MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/browser/refresh")
				.header("X-XSRF-TOKEN", csrf)
				.cookie(new Cookie("XSRF-TOKEN", csrf), refresh1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").doesNotExist())
				.andExpect(cookie().exists(refreshCookieName()))
				.andReturn();
		Cookie refresh2 = refreshed.getResponse().getCookie(refreshCookieName());
		assertThat(refresh2).isNotNull();
		assertThat(refresh2.getValue()).isNotEqualTo(refresh1.getValue());

		csrf = bootstrapCsrf();
		mockMvc.perform(post("/api/v1/auth/browser/refresh")
				.header("X-XSRF-TOKEN", csrf)
				.cookie(new Cookie("XSRF-TOKEN", csrf), refresh1))
				.andExpect(status().isUnauthorized());

		// Existing JSON token API remains usable for non-browser clients.
		MvcResult jsonLogin = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn();
		String jsonRefresh = jsonMapper.readTree(jsonLogin.getResponse().getContentAsString())
				.get("refreshToken").asText();
		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken":"%s"}
						""".formatted(jsonRefresh)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	void browserLogoutRevokesAndClearsCookie() throws Exception {
		String email = "logout-" + UUID.randomUUID() + "@example.com";
		String password = "Str0ngPass!";
		register(email, password);

		String csrf = bootstrapCsrf();
		MvcResult login = mockMvc.perform(post("/api/v1/auth/browser/login")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-XSRF-TOKEN", csrf)
				.cookie(new Cookie("XSRF-TOKEN", csrf))
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		Cookie refresh = login.getResponse().getCookie(refreshCookieName());
		assertThat(refresh).isNotNull();

		csrf = bootstrapCsrf();
		mockMvc.perform(post("/api/v1/auth/browser/logout")
				.header("X-XSRF-TOKEN", csrf)
				.cookie(new Cookie("XSRF-TOKEN", csrf), refresh))
				.andExpect(status().isNoContent())
				.andExpect(cookie().maxAge(refreshCookieName(), 0));

		csrf = bootstrapCsrf();
		mockMvc.perform(post("/api/v1/auth/browser/refresh")
				.header("X-XSRF-TOKEN", csrf)
				.cookie(new Cookie("XSRF-TOKEN", csrf), refresh))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void missingOrExpiredRefreshRejected() throws Exception {
		String csrf = bootstrapCsrf();
		mockMvc.perform(post("/api/v1/auth/browser/refresh")
				.header("X-XSRF-TOKEN", csrf)
				.cookie(new Cookie("XSRF-TOKEN", csrf)))
				.andExpect(status().isUnauthorized());

		csrf = bootstrapCsrf();
		Cookie bogus = new Cookie(refreshCookieName(), "not-a-real-refresh-token-value-xxxxxxxx");
		bogus.setPath(securityProperties.getBrowserSession().getRefreshCookiePath());
		mockMvc.perform(post("/api/v1/auth/browser/refresh")
				.header("X-XSRF-TOKEN", csrf)
				.cookie(new Cookie("XSRF-TOKEN", csrf), bogus))
				.andExpect(status().isUnauthorized())
				.andExpect(cookie().maxAge(refreshCookieName(), 0));
	}

	private String bootstrapCsrf() throws Exception {
		MvcResult csrf = mockMvc.perform(get("/api/v1/auth/browser/csrf"))
				.andExpect(status().isNoContent())
				.andExpect(cookie().exists("XSRF-TOKEN"))
				.andExpect(cookie().httpOnly("XSRF-TOKEN", false))
				.andReturn();
		Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
		assertThat(cookie).isNotNull();
		return cookie.getValue();
	}

	private void register(String email, String password) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fullName":"Browser User","email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isCreated());
	}

	private String refreshCookieName() {
		return securityProperties.getBrowserSession().getRefreshCookieName();
	}
}
