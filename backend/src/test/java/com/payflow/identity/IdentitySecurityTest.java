package com.payflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.payflow.TestcontainersConfiguration;
import com.payflow.shared.security.JwtTokenService;
import com.payflow.shared.security.PayflowSecurityProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IdentitySecurityTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private JwtEncoder jwtEncoder;
	@Autowired
	private PayflowSecurityProperties securityProperties;

	@Test
	void registerLoginMeRefreshReuseLogoutFlow() throws Exception {
		String email = "alice-" + UUID.randomUUID() + "@example.com";
		String password = "Str0ngPass!";

		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fullName":"Alice Example","email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.roles[0]").value("USER"));

		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fullName":"Alice Example","email":"%s","password":"%s"}
						""".formatted(email.toUpperCase(), password)))
				.andExpect(status().isConflict());

		String hash = jdbcTemplate.queryForObject(
				"SELECT password_hash FROM users WHERE email_normalized = ?",
				String.class,
				email.toLowerCase());
		assertThat(hash).isNotBlank().isNotEqualTo(password);

		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn();

		JsonNode tokens = jsonMapper.readTree(login.getResponse().getContentAsString());
		String access = tokens.get("accessToken").asText();
		String refresh = tokens.get("refreshToken").asText();

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"WrongPass1!"}
						""".formatted(email)))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));

		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized());

		Integer plaintextRefresh = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?",
				Integer.class,
				refresh);
		assertThat(plaintextRefresh).isZero();

		MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken":"%s"}
						""".formatted(refresh)))
				.andExpect(status().isOk())
				.andReturn();
		String refresh2 = jsonMapper.readTree(refreshed.getResponse().getContentAsString())
				.get("refreshToken").asText();

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken":"%s"}
						""".formatted(refresh)))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken":"%s"}
						""".formatted(refresh2)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken":"%s"}
						""".formatted(refresh2)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void concurrentRefreshOnlyOneSucceeds() throws Exception {
		String email = "race-" + UUID.randomUUID() + "@example.com";
		String password = "Str0ngPass!";
		registerAndLogin(email, password);
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		String refresh = jsonMapper.readTree(login.getResponse().getContentAsString())
				.get("refreshToken").asText();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger failures = new AtomicInteger();
		Runnable task = () -> {
			try {
				start.await();
				int status = mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refresh)))
						.andReturn()
						.getResponse()
						.getStatus();
				if (status == 200) {
					successes.incrementAndGet();
				}
				else {
					failures.incrementAndGet();
				}
			}
			catch (Exception e) {
				failures.incrementAndGet();
			}
		};
		Future<?> f1 = pool.submit(task);
		Future<?> f2 = pool.submit(task);
		start.countDown();
		f1.get();
		f2.get();
		pool.shutdownNow();
		assertThat(successes.get()).isEqualTo(1);
		assertThat(failures.get()).isEqualTo(1);
	}

	@Test
	void invalidAndTamperedJwtRejected() throws Exception {
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized());

		String email = "jwt-" + UUID.randomUUID() + "@example.com";
		String password = "Str0ngPass!";
		registerAndLogin(email, password);
		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andReturn();
		String access = jsonMapper.readTree(login.getResponse().getContentAsString())
				.get("accessToken").asText();
		String tampered = access.substring(0, access.length() - 4) + "xxxx";
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tampered))
				.andExpect(status().isUnauthorized());

		String wrongAudience = encodeToken(UUID.randomUUID(), Instant.now().plusSeconds(60),
				securityProperties.getJwt().getIssuer(), "wrong-audience");
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + wrongAudience))
				.andExpect(status().isUnauthorized());

		String wrongIssuer = encodeToken(UUID.randomUUID(), Instant.now().plusSeconds(60),
				"evil-issuer", securityProperties.getJwt().getAudience());
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + wrongIssuer))
				.andExpect(status().isUnauthorized());

		// Use >60s past expiry to exceed default JWT clock skew.
		String expired = encodeToken(UUID.randomUUID(), Instant.now().minusSeconds(120),
				securityProperties.getJwt().getIssuer(), securityProperties.getJwt().getAudience());
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized());
	}

	private void registerAndLogin(String email, String password) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fullName":"Test User","email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isCreated());
	}

	private String encodeToken(UUID subject, Instant expires, String issuer, String audience) {
		Instant issuedAt = expires.minusSeconds(60);
		if (issuedAt.isAfter(expires) || !issuedAt.isBefore(expires)) {
			issuedAt = expires.minusSeconds(1);
		}
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.audience(List.of(audience))
				.issuedAt(issuedAt)
				.expiresAt(expires)
				.subject(subject.toString())
				.claim(JwtTokenService.CLAIM_TOKEN_USE, JwtTokenService.TOKEN_USE_ACCESS)
				.claim(JwtTokenService.CLAIM_ROLES, List.of("USER"))
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
	}
}
