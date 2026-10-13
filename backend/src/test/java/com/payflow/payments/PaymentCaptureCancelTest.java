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
class PaymentCaptureCancelTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void capturesAuthorizedPaymentAndRejectsCreatedCapture() throws Exception {
		String ownerToken = registerLogin("cap-ok-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId, "ord-cap-ok", 2200);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("invalid_state_transition"));

		String processorRef = authorize(ownerToken, paymentId);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CAPTURED"))
				.andExpect(jsonPath("$.capturedAt").isNotEmpty())
				.andExpect(jsonPath("$.cancelledAt").value(org.hamcrest.Matchers.nullValue()));

		var row = jdbcTemplate.queryForMap(
				"SELECT status, processor_ref, captured_at, cancelled_at FROM payments WHERE id = ?",
				UUID.fromString(paymentId));
		assertThat(row.get("status")).isEqualTo("CAPTURED");
		assertThat(row.get("processor_ref")).isEqualTo(processorRef);
		assertThat(row.get("captured_at")).isNotNull();
		assertThat(row.get("cancelled_at")).isNull();

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("invalid_state_transition"));
	}

	@Test
	void cancelsCreatedAndAuthorizedPayments() throws Exception {
		String ownerToken = registerLogin("cancel-ok-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		String createdId = createPayment(ownerToken, merchantId, "ord-cancel-created", 1100);
		mockMvc.perform(post("/api/v1/payments/" + createdId + "/cancel")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancelledAt").isNotEmpty());

		assertThat(jdbcTemplate.queryForMap(
				"SELECT cancelled_at FROM payments WHERE id = ?", UUID.fromString(createdId)).get("cancelled_at"))
				.isNotNull();

		mockMvc.perform(post("/api/v1/payments/" + createdId + "/cancel")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("invalid_state_transition"));

		String authorizedId = createPayment(ownerToken, merchantId, "ord-cancel-auth", 1300);
		authorize(ownerToken, authorizedId);
		mockMvc.perform(post("/api/v1/payments/" + authorizedId + "/cancel")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancelledAt").isNotEmpty());
	}

	@Test
	void rejectsInvalidTransitionsSecurityAndFailedPayments() throws Exception {
		String ownerToken = registerLogin("cap-sec-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("cap-other-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		String failedId = createPayment(ownerToken, merchantId,
				DeterministicSimulatedPaymentProcessor.REF_PREFIX_DECLINE + "x", 900);
		mockMvc.perform(post("/api/v1/payments/" + failedId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"));

		mockMvc.perform(post("/api/v1/payments/" + failedId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isConflict());
		mockMvc.perform(post("/api/v1/payments/" + failedId + "/cancel")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isConflict());

		String paymentId = createPayment(ownerToken, merchantId, "ord-cap-sec", 800);
		authorize(ownerToken, paymentId);

		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
				.header("Authorization", "Bearer " + otherToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void concurrentCaptureVersusCancelProducesOneTerminalState() throws Exception {
		String ownerToken = registerLogin("cap-race-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId, "ord-cap-race", 1500);
		authorize(ownerToken, paymentId);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();

		futures.add(pool.submit(() -> {
			ready.countDown();
			start.await(10, TimeUnit.SECONDS);
			return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/capture")
					.header("Authorization", "Bearer " + ownerToken))
					.andReturn();
		}));
		futures.add(pool.submit(() -> {
			ready.countDown();
			start.await(10, TimeUnit.SECONDS);
			return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
					.header("Authorization", "Bearer " + ownerToken))
					.andReturn();
		}));

		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		int success = 0;
		int conflicts = 0;
		List<String> successStatuses = new ArrayList<>();
		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(30, TimeUnit.SECONDS);
			int status = result.getResponse().getStatus();
			if (status == 200) {
				success++;
				successStatuses.add(jsonMapper.readTree(result.getResponse().getContentAsString())
						.get("status").asText());
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
		assertThat(conflicts).isEqualTo(1);
		assertThat(successStatuses).hasSize(1);
		assertThat(successStatuses.get(0)).isIn("CAPTURED", "CANCELLED");

		var row = jdbcTemplate.queryForMap(
				"SELECT status, captured_at, cancelled_at, processor_ref FROM payments WHERE id = ?",
				UUID.fromString(paymentId));
		String dbStatus = row.get("status").toString();
		assertThat(dbStatus).isEqualTo(successStatuses.get(0));
		if ("CAPTURED".equals(dbStatus)) {
			assertThat(row.get("captured_at")).isNotNull();
			assertThat(row.get("cancelled_at")).isNull();
		}
		else {
			assertThat(row.get("cancelled_at")).isNotNull();
			assertThat(row.get("captured_at")).isNull();
		}
		assertThat(row.get("processor_ref")).isNotNull();
	}

	private String authorize(String ownerToken, String paymentId) throws Exception {
		MvcResult authorized = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"))
				.andReturn();
		String paymentUuid = jsonMapper.readTree(authorized.getResponse().getContentAsString())
				.get("paymentId").asText();
		return jdbcTemplate.queryForObject(
				"SELECT processor_ref FROM payments WHERE id = ?",
				String.class,
				UUID.fromString(paymentUuid));
	}

	private String createPayment(String accessToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", "cap-key-" + UUID.randomUUID())
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
						{"name":"Capture Merchant","description":"Capture/cancel test"}
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
