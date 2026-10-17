package com.payflow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.payflow.TestcontainersConfiguration;
import com.payflow.shared.security.PayflowSecurityProperties;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
		"payflow.security.cors.allowed-origins=https://app.example.com",
		"payflow.security.browser-session.cookie-secure=true",
		"springdoc.api-docs.enabled=false",
		"springdoc.swagger-ui.enabled=false",
		"server.error.include-stacktrace=never",
		"server.error.include-message=never"
})
class ProductionHardeningTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PayflowSecurityProperties securityProperties;

	@Test
	void corsRejectsLocalhostOriginWhenOnlyProductionOriginConfigured() throws Exception {
		assertThat(securityProperties.getCors().getAllowedOrigins())
				.isEqualTo(List.of("https://app.example.com"));

		mockMvc.perform(options("/api/v1/system/ping")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
	}

	@Test
	void corsAllowsConfiguredProductionOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/system/ping")
						.header(HttpHeaders.ORIGIN, "https://app.example.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.com"));
	}

	@Test
	void swaggerUiIsNotServedWhenDisabled() throws Exception {
		// springdoc disabled: must not expose usable OpenAPI UI/docs (status is not 2xx).
		int swaggerStatus = mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse().getStatus();
		int docsStatus = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getStatus();
		assertThat(swaggerStatus).isGreaterThanOrEqualTo(400);
		assertThat(docsStatus).isGreaterThanOrEqualTo(400);
	}

	@Test
	void csrfCookieIsSecureWhenBrowserSessionRequiresSecure() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/browser/csrf"))
				.andExpect(status().isNoContent())
				.andReturn();
		List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
		assertThat(setCookies).anySatisfy(cookie -> {
			assertThat(cookie).contains("XSRF-TOKEN=");
			assertThat(cookie).contains("Secure");
			assertThat(cookie).doesNotContain("HttpOnly");
		});
	}

	@Test
	void validationErrorsDoNotExposeStackTraces() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_error"))
				.andReturn();
		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain("Exception");
		assertThat(body).doesNotContain("at com.payflow");
		assertThat(body).doesNotContain("stackTrace");
	}
}
