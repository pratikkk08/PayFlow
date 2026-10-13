package com.payflow.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
class PaymentStateHardeningTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void validTransitionsIncrementVersionAndKeepFieldsConsistent() throws Exception {
		String ownerToken = registerLogin("hard-valid-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		String authorizeId = createPayment(ownerToken, merchantId, "hard-auth", 2000);
		assertVersion(authorizeId, 0);
		mockMvc.perform(post("/api/v1/payments/" + authorizeId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));
		Map<String, Object> authorized = row(authorizeId);
		assertThat(authorized.get("status")).isEqualTo("AUTHORIZED");
		assertThat(authorized.get("authorized_at")).isNotNull();
		assertThat(authorized.get("captured_at")).isNull();
		assertThat(authorized.get("cancelled_at")).isNull();
		assertThat(authorized.get("failure_code")).isNull();
		assertThat(authorized.get("processor_ref")).isNotNull();
		assertVersion(authorizeId, 1);

		String failId = createPayment(ownerToken, merchantId,
				DeterministicSimulatedPaymentProcessor.REF_PREFIX_DECLINE + "hard", 2100);
		mockMvc.perform(post("/api/v1/payments/" + failId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.failureCode")
						.value(DeterministicSimulatedPaymentProcessor.FAILURE_SIMULATED_DECLINE));
		Map<String, Object> failed = row(failId);
		assertThat(failed.get("failure_code"))
				.isEqualTo(DeterministicSimulatedPaymentProcessor.FAILURE_SIMULATED_DECLINE);
		assertThat(failed.get("processor_ref")).isNull();
		assertThat(failed.get("authorized_at")).isNull();
		assertVersion(failId, 1);

		String cancelCreatedId = createPayment(ownerToken, merchantId, "hard-cancel-created", 2200);
		mockMvc.perform(post("/api/v1/payments/" + cancelCreatedId + "/cancel")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(row(cancelCreatedId).get("cancelled_at")).isNotNull();
		assertThat(row(cancelCreatedId).get("authorized_at")).isNull();
		assertVersion(cancelCreatedId, 1);

		String captureId = createPayment(ownerToken, merchantId, "hard-capture", 2300);
		String processorRef = authorize(ownerToken, captureId);
		assertVersion(captureId, 1);
		mockMvc.perform(post("/api/v1/payments/" + captureId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CAPTURED"));
		Map<String, Object> captured = row(captureId);
		assertThat(captured.get("captured_at")).isNotNull();
		assertThat(captured.get("cancelled_at")).isNull();
		assertThat(captured.get("processor_ref")).isEqualTo(processorRef);
		assertThat(captured.get("authorized_at")).isNotNull();
		assertVersion(captureId, 2);

		String cancelAuthId = createPayment(ownerToken, merchantId, "hard-cancel-auth", 2400);
		authorize(ownerToken, cancelAuthId);
		mockMvc.perform(post("/api/v1/payments/" + cancelAuthId + "/cancel")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		Map<String, Object> cancelledAuth = row(cancelAuthId);
		assertThat(cancelledAuth.get("cancelled_at")).isNotNull();
		assertThat(cancelledAuth.get("authorized_at")).isNotNull();
		assertThat(cancelledAuth.get("captured_at")).isNull();
		assertVersion(cancelAuthId, 2);
	}

	@Test
	void invalidTransitionsRejectedFromEveryState() throws Exception {
		String ownerToken = registerLogin("hard-invalid-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		String createdId = createPayment(ownerToken, merchantId, "hard-inv-created", 1000);
		expectConflict(ownerToken, createdId, "capture");

		String authorizedId = createPayment(ownerToken, merchantId, "hard-inv-auth", 1100);
		authorize(ownerToken, authorizedId);
		expectConflict(ownerToken, authorizedId, "authorize");

		String failedId = createPayment(ownerToken, merchantId,
				DeterministicSimulatedPaymentProcessor.REF_PREFIX_DECLINE + "inv", 1200);
		mockMvc.perform(post("/api/v1/payments/" + failedId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());
		expectConflict(ownerToken, failedId, "authorize");
		expectConflict(ownerToken, failedId, "capture");
		expectConflict(ownerToken, failedId, "cancel");

		String capturedId = createPayment(ownerToken, merchantId, "hard-inv-cap", 1300);
		authorize(ownerToken, capturedId);
		mockMvc.perform(post("/api/v1/payments/" + capturedId + "/capture")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());
		expectConflict(ownerToken, capturedId, "authorize");
		expectConflict(ownerToken, capturedId, "capture");
		expectConflict(ownerToken, capturedId, "cancel");

		String cancelledId = createPayment(ownerToken, merchantId, "hard-inv-can", 1400);
		mockMvc.perform(post("/api/v1/payments/" + cancelledId + "/cancel")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk());
		expectConflict(ownerToken, cancelledId, "authorize");
		expectConflict(ownerToken, cancelledId, "capture");
		expectConflict(ownerToken, cancelledId, "cancel");
	}

	@Test
	void concurrentAuthorizeVersusCancelAndDuplicateAuthorize() throws Exception {
		String ownerToken = registerLogin("hard-race-ac-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		String raceId = createPayment(ownerToken, merchantId, "hard-auth-cancel", 1600);
		RaceResult authCancel = race(ownerToken, raceId, "authorize", "cancel");
		// Cancel-first → 1 success (CANCELLED) + 1 conflict.
		// Authorize-first → authorize 200 then cancel 200 (AUTHORIZED→CANCELLED is valid).
		assertThat(authCancel.successes()).isBetween(1, 2);
		assertThat(authCancel.successes() + authCancel.conflicts()).isEqualTo(2);
		assertThat(row(raceId).get("status")).isEqualTo("CANCELLED");
		assertThat(((Number) row(raceId).get("version")).longValue()).isBetween(1L, 2L);

		String dupAuthId = createPayment(ownerToken, merchantId, "hard-auth-auth", 1700);
		RaceResult dupAuth = raceMany(ownerToken, dupAuthId, "authorize", 6);
		assertThat(dupAuth.successes()).isEqualTo(1);
		assertThat(dupAuth.conflicts()).isEqualTo(5);
		assertThat(row(dupAuthId).get("status")).isEqualTo("AUTHORIZED");
		assertVersion(dupAuthId, 1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE merchant_id = ? AND merchant_reference = 'hard-auth-auth'",
				Integer.class,
				UUID.fromString(merchantId))).isEqualTo(1);
	}

	@Test
	void concurrentCaptureVersusCancelAndDuplicateCapture() throws Exception {
		String ownerToken = registerLogin("hard-race-cc-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);

		String raceId = createPayment(ownerToken, merchantId, "hard-cap-cancel", 1800);
		String processorRef = authorize(ownerToken, raceId);
		RaceResult capCancel = race(ownerToken, raceId, "capture", "cancel");
		assertThat(capCancel.successes()).isEqualTo(1);
		assertThat(capCancel.conflicts()).isEqualTo(1);
		String terminal = capCancel.successStatuses().get(0);
		assertThat(terminal).isIn("CAPTURED", "CANCELLED");
		Map<String, Object> raced = row(raceId);
		assertThat(raced.get("status")).isEqualTo(terminal);
		assertThat(raced.get("processor_ref")).isEqualTo(processorRef);
		assertVersion(raceId, 2);

		String dupCapId = createPayment(ownerToken, merchantId, "hard-cap-cap", 1900);
		authorize(ownerToken, dupCapId);
		RaceResult dupCap = raceMany(ownerToken, dupCapId, "capture", 6);
		assertThat(dupCap.successes()).isEqualTo(1);
		assertThat(dupCap.conflicts()).isEqualTo(5);
		assertThat(row(dupCapId).get("status")).isEqualTo("CAPTURED");
		assertVersion(dupCapId, 2);
	}

	@Test
	void concurrentCrossMerchantActionsCannotMutatePayment() throws Exception {
		String ownerToken = registerLogin("hard-owner-" + UUID.randomUUID() + "@example.com");
		String otherToken = registerLogin("hard-other-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(ownerToken);
		String paymentId = createPayment(ownerToken, merchantId, "hard-xmerchant", 1950);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Future<MvcResult> ownerFuture = pool.submit(() -> {
			ready.countDown();
			start.await(10, TimeUnit.SECONDS);
			return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
					.header("Authorization", "Bearer " + ownerToken))
					.andReturn();
		});
		Future<MvcResult> otherFuture = pool.submit(() -> {
			ready.countDown();
			start.await(10, TimeUnit.SECONDS);
			return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel")
					.header("Authorization", "Bearer " + otherToken))
					.andReturn();
		});
		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		assertThat(ownerFuture.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
		assertThat(otherFuture.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(404);
		pool.shutdown();

		assertThat(row(paymentId).get("status")).isEqualTo("AUTHORIZED");
		assertVersion(paymentId, 1);
	}

	private void expectConflict(String token, String paymentId, String action) throws Exception {
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/" + action)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("invalid_state_transition"));
	}

	private RaceResult race(String token, String paymentId, String firstAction, String secondAction) throws Exception {
		return raceActions(token, paymentId, List.of(firstAction, secondAction));
	}

	private RaceResult raceMany(String token, String paymentId, String action, int threads) throws Exception {
		List<String> actions = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			actions.add(action);
		}
		return raceActions(token, paymentId, actions);
	}

	private RaceResult raceActions(String token, String paymentId, List<String> actions) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(actions.size());
		CountDownLatch ready = new CountDownLatch(actions.size());
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();
		for (String action : actions) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				start.await(10, TimeUnit.SECONDS);
				return mockMvc.perform(post("/api/v1/payments/" + paymentId + "/" + action)
						.header("Authorization", "Bearer " + token))
						.andReturn();
			}));
		}
		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		int successes = 0;
		int conflicts = 0;
		List<String> successStatuses = new ArrayList<>();
		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(30, TimeUnit.SECONDS);
			int status = result.getResponse().getStatus();
			if (status == 200) {
				successes++;
				successStatuses.add(jsonMapper.readTree(result.getResponse().getContentAsString())
						.get("status").asText());
			}
			else if (status == 409) {
				conflicts++;
			}
			else {
				throw new AssertionError("Unexpected status " + status + " body="
						+ result.getResponse().getContentAsString());
			}
		}
		pool.shutdown();
		return new RaceResult(successes, conflicts, successStatuses);
	}

	private record RaceResult(int successes, int conflicts, List<String> successStatuses) {
	}

	private Map<String, Object> row(String paymentId) {
		return jdbcTemplate.queryForMap("SELECT * FROM payments WHERE id = ?", UUID.fromString(paymentId));
	}

	private void assertVersion(String paymentId, long expected) {
		Long version = jdbcTemplate.queryForObject(
				"SELECT version FROM payments WHERE id = ?",
				Long.class,
				UUID.fromString(paymentId));
		assertThat(version).isEqualTo(expected);
	}

	private String authorize(String ownerToken, String paymentId) throws Exception {
		mockMvc.perform(post("/api/v1/payments/" + paymentId + "/authorize")
				.header("Authorization", "Bearer " + ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("AUTHORIZED"));
		return jdbcTemplate.queryForObject(
				"SELECT processor_ref FROM payments WHERE id = ?",
				String.class,
				UUID.fromString(paymentId));
	}

	private String createPayment(String accessToken, String merchantId, String reference, long amountMinor)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/payments")
				.header("Authorization", "Bearer " + accessToken)
				.header("Idempotency-Key", "hard-key-" + UUID.randomUUID())
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
						{"name":"Hardening Merchant","description":"State hardening"}
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
