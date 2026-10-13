package com.payflow.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
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

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PaymentAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void authorizesSuccessfullyWithDeterministicProcessor() throws Exception {
		String ownerToken = registerLogin("auth-ok-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId, "ord-auth-ok", 2500);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paymentId").value(paymentId))
				.andExpect(jsonPath("$.status").value("AUTHORIZED"))
				.andExpect(jsonPath("$.failureCode").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.authorizedAt").isNotEmpty());

		var row = jdbcTemplate.queryForMap("SELECT status, failure_code, processor_ref, authorized_at FROM payments WHERE id = ?",
				UUID.fromString(paymentId));
		assertThat(row.get("status")).isEqualTo("AUTHORIZED");
		assertThat(row.get("failure_code")).isNull();
		assertThat(row.get("processor_ref").toString()).startsWith("sim_auth_");
		assertThat(row.get("authorized_at")).isNotNull();
	}

	@Test
	void simulatesAuthorizationFailureFromMerchantReferenceScenario() throws Exception {
		String ownerToken = registerLogin("auth-fail-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId,
				DeterministicSimulatedPaymentProcessor.REF_PREFIX_DECLINE + "card", 1800);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureCode").value(DeterministicSimulatedPaymentProcessor.FAILURE_SIMULATED_DECLINE))
				.andExpect(jsonPath("$.authorizedAt").value(org.hamcrest.Matchers.nullValue()));

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE merchant_id = ?",
				Integer.class,
				UUID.fromString(merchantId));
		assertThat(count).isEqualTo(1);
	}

	@Test
	void rejectsUnauthenticatedCrossMerchantAndInvalidTransitions() throws Exception {
		String ownerToken = registerLogin("auth-sec-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("auth-other-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId, "ord-auth-sec", 900);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("invalid_state_transition"));
	}

	@Test
	void concurrentAuthorizationsProduceExactlyOneSuccessfulTransition() throws Exception {
		String ownerToken = registerLogin("auth-conc-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId, "ord-auth-conc", 1200);

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
				return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
						.header("Authorization", "Bearer " + ownerToken))
						.andReturn();
			}));
		}

		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		int success = 0;
		int conflicts = 0;
		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(30, TimeUnit.SECONDS);
			int status = result.getResponse().getStatus();
			if (status == 200) {
				success++;
				assertThat(jsonMapper.readTree(result.getResponse().getContentAsString())
						.get("status").asText()).isEqualTo("AUTHORIZED");
			}
			else if (status == 409) {
				conflicts++;
			}
			else {
				throw new AssertionError("Unexpected status " + status);
			}
		}
		pool.shutdown();

		assertThat(success).isEqualTo(1);
		assertThat(conflicts).isEqualTo(threads - 1);

		Integer payments = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE id = ?",
				Integer.class,
				UUID.fromString(paymentId));
		assertThat(payments).isEqualTo(1);

		String dbStatus = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?",
				String.class,
				UUID.fromString(paymentId));
		assertThat(dbStatus).isEqualTo("AUTHORIZED");
	}

	private String createPayment(String accessToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", "auth-key-" + UUID.randomUUID())
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
						{"name":"Auth Merchant","description":"Authorization test"}
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
