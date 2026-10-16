package com.payflow.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import com.payflow.analytics.service.MerchantAnalyticsService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MerchantAnalyticsTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void overview_amountsRefundsNetRiskAndCaptureRate() throws Exception {
		String token = registerLogin("analytics-amt-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		UUID capturedPartial = insertPayment(merchantId, "CREATED", 10_000,
				Instant.parse("2026-03-10T10:00:00Z"));
		markCaptured(capturedPartial, Instant.parse("2026-03-11T12:00:00Z"), 10_000);

		UUID capturedFull = insertPayment(merchantId, "CREATED", 5_000,
				Instant.parse("2026-03-12T10:00:00Z"));
		markCaptured(capturedFull, Instant.parse("2026-03-12T15:00:00Z"), 5_000);

		insertRefund(merchantId, capturedPartial, 3_000, "SUCCEEDED",
				Instant.parse("2026-03-13T09:00:00Z"));
		insertRefund(merchantId, capturedFull, 5_000, "SUCCEEDED",
				Instant.parse("2026-03-13T10:00:00Z"));
		insertRefund(merchantId, capturedPartial, 1_000, "FAILED",
				Instant.parse("2026-03-13T11:00:00Z"));
		insertRefund(merchantId, capturedPartial, 500, "PENDING", null);

		UUID createdOnly = insertPayment(merchantId, "CREATED", 2_000,
				Instant.parse("2026-03-14T08:00:00Z"));
		assertThat(createdOnly).isNotNull();

		UUID blockedPayment = insertPayment(merchantId, "FAILED", 9_000,
				Instant.parse("2026-03-14T09:00:00Z"));
		insertRiskAssessment(merchantId, blockedPayment, "BLOCK",
				Instant.parse("2026-03-14T09:05:00Z"));
		UUID reviewPayment = insertPayment(merchantId, "CREATED", 1_500,
				Instant.parse("2026-03-14T10:00:00Z"));
		insertRiskAssessment(merchantId, reviewPayment, "REVIEW",
				Instant.parse("2026-03-14T10:05:00Z"));

		MvcResult result = mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/analytics/overview")
				.header("Authorization", "Bearer " + token)
				.param("from", "2026-03-10")
				.param("to", "2026-03-14"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reportingTimezone").value("UTC"))
				.andExpect(jsonPath("$.currency").value("INR"))
				.andExpect(jsonPath("$.totalCapturedAmountMinor").value(15_000))
				.andExpect(jsonPath("$.totalSucceededRefundAmountMinor").value(8_000))
				.andExpect(jsonPath("$.netCapturedAmountMinor").value(7_000))
				.andExpect(jsonPath("$.riskBlockCount").value(1))
				.andExpect(jsonPath("$.riskReviewCount").value(1))
				.andExpect(jsonPath("$.createdCount").value(2))
				.andExpect(jsonPath("$.capturedCount").value(2))
				.andExpect(jsonPath("$.failedCount").value(1))
				.andExpect(jsonPath("$.totalPaymentCount").value(5))
				.andReturn();

		JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.get("paymentCaptureRate").decimalValue())
				.isEqualByComparingTo(new BigDecimal("0.400000"));
		assertThat(body.get("paymentCaptureRateDefinition").asText())
				.contains("capturedCount / totalPaymentCount");
		assertThat(body.get("disclaimer").asText()).contains("not settled bank balances");
	}

	@Test
	void daily_usesEventTimestampsAndUtcDayBoundaries() throws Exception {
		String token = registerLogin("analytics-daily-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		UUID lateCreate = insertPayment(merchantId, "CREATED", 1_000,
				Instant.parse("2026-04-01T23:30:00Z"));
		UUID earlyNext = insertPayment(merchantId, "CREATED", 2_000,
				Instant.parse("2026-04-02T00:30:00Z"));

		UUID captureCross = insertPayment(merchantId, "CREATED", 4_000,
				Instant.parse("2026-03-31T12:00:00Z"));
		markCaptured(captureCross, Instant.parse("2026-04-01T23:59:00Z"), 4_000);

		UUID failed = insertPayment(merchantId, "FAILED", 3_000,
				Instant.parse("2026-04-01T10:00:00Z"));
		jdbcTemplate.update("UPDATE payments SET updated_at = ? WHERE id = ?",
				java.sql.Timestamp.from(Instant.parse("2026-04-02T01:00:00Z")), failed);

		insertRefund(merchantId, captureCross, 500, "SUCCEEDED",
				Instant.parse("2026-04-02T02:00:00Z"));

		MvcResult result = mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/analytics/daily")
				.header("Authorization", "Bearer " + token)
				.param("from", "2026-04-01")
				.param("to", "2026-04-02"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days").isArray())
				.andExpect(jsonPath("$.days.length()").value(2))
				.andReturn();

		JsonNode days = jsonMapper.readTree(result.getResponse().getContentAsString()).get("days");
		JsonNode day1 = days.get(0);
		JsonNode day2 = days.get(1);

		assertThat(day1.get("date").asText()).isEqualTo("2026-04-01");
		assertThat(day1.get("paymentsCreated").asLong()).isEqualTo(2); // lateCreate + failed created
		assertThat(day1.get("paymentsCaptured").asLong()).isEqualTo(1);
		assertThat(day1.get("capturedAmountMinor").asLong()).isEqualTo(4_000);
		assertThat(day1.get("paymentsFailed").asLong()).isEqualTo(0);
		assertThat(day1.get("succeededRefundAmountMinor").asLong()).isEqualTo(0);
		assertThat(day1.get("netAmountMinor").asLong()).isEqualTo(4_000);

		assertThat(day2.get("date").asText()).isEqualTo("2026-04-02");
		assertThat(day2.get("paymentsCreated").asLong()).isEqualTo(1); // earlyNext only
		assertThat(day2.get("paymentsCaptured").asLong()).isEqualTo(0);
		assertThat(day2.get("paymentsFailed").asLong()).isEqualTo(1);
		assertThat(day2.get("succeededRefundAmountMinor").asLong()).isEqualTo(500);
		assertThat(day2.get("netAmountMinor").asLong()).isEqualTo(-500);

		assertThat(lateCreate).isNotNull();
		assertThat(earlyNext).isNotNull();
	}

	@Test
	void daily_emptyPeriodReturnsZeroRowsPerDay_noPagination() throws Exception {
		String token = registerLogin("analytics-empty-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		MvcResult result = mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/analytics/daily")
				.header("Authorization", "Bearer " + token)
				.param("from", "2026-05-01")
				.param("to", "2026-05-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days.length()").value(3))
				.andReturn();

		JsonNode days = jsonMapper.readTree(result.getResponse().getContentAsString()).get("days");
		for (JsonNode day : days) {
			assertThat(day.get("paymentsCreated").asLong()).isZero();
			assertThat(day.get("paymentsCaptured").asLong()).isZero();
			assertThat(day.get("paymentsFailed").asLong()).isZero();
			assertThat(day.get("capturedAmountMinor").asLong()).isZero();
			assertThat(day.get("succeededRefundAmountMinor").asLong()).isZero();
			assertThat(day.get("netAmountMinor").asLong()).isZero();
		}

		MvcResult overview = mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/analytics/overview")
				.header("Authorization", "Bearer " + token)
				.param("from", "2026-05-01")
				.param("to", "2026-05-03"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalPaymentCount").value(0))
				.andExpect(jsonPath("$.totalCapturedAmountMinor").value(0))
				.andExpect(jsonPath("$.netCapturedAmountMinor").value(0))
				.andReturn();
		assertThat(jsonMapper.readTree(overview.getResponse().getContentAsString())
				.get("paymentCaptureRate").isNull()).isTrue();
	}

	@Test
	void overview_stateCountsDistinctFromCaptureEventAmounts() throws Exception {
		String token = registerLogin("analytics-state-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		// Created outside window, captured inside — contributes to amount, not status counts
		UUID outsideCreate = insertPayment(merchantId, "CREATED", 8_000,
				Instant.parse("2026-01-01T10:00:00Z"));
		markCaptured(outsideCreate, Instant.parse("2026-06-15T12:00:00Z"), 8_000);

		// Created inside window, still CREATED — status count only
		insertPayment(merchantId, "CREATED", 1_000, Instant.parse("2026-06-15T08:00:00Z"));

		mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/analytics/overview")
				.header("Authorization", "Bearer " + token)
				.param("from", "2026-06-15")
				.param("to", "2026-06-15"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalPaymentCount").value(1))
				.andExpect(jsonPath("$.createdCount").value(1))
				.andExpect(jsonPath("$.capturedCount").value(0))
				.andExpect(jsonPath("$.totalCapturedAmountMinor").value(8_000));
	}

	@Test
	void rejectsInvalidRanges() throws Exception {
		String token = registerLogin("analytics-range-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/analytics/overview")
				.header("Authorization", "Bearer " + token)
				.param("from", "2026-06-10")
				.param("to", "2026-06-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_reporting_range"));

		mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/analytics/daily")
				.header("Authorization", "Bearer " + token)
				.param("from", "2024-01-01")
				.param("to", "2026-01-02"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_reporting_range"));
	}

	@Test
	void crossMerchantIsolation_andAuthGuards() throws Exception {
		String ownerA = registerLogin("analytics-a-" + UUID.randomUUID() + "@example.com");
		String ownerB = registerLogin("analytics-b-" + UUID.randomUUID() + "@example.com");
		String merchantA = createMerchant(ownerA);
		String merchantB = createMerchant(ownerB);

		UUID paymentA = insertPayment(merchantA, "CREATED", 7_000, Instant.parse("2026-07-01T10:00:00Z"));
		markCaptured(paymentA, Instant.parse("2026-07-01T11:00:00Z"), 7_000);
		UUID paymentB = insertPayment(merchantB, "CREATED", 9_000, Instant.parse("2026-07-01T10:00:00Z"));
		markCaptured(paymentB, Instant.parse("2026-07-01T11:00:00Z"), 9_000);

		mockMvc.perform(get("/api/v1/merchants/" + merchantA + "/analytics/overview")
				.header("Authorization", "Bearer " + ownerA)
				.param("from", "2026-07-01")
				.param("to", "2026-07-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCapturedAmountMinor").value(7_000));

		mockMvc.perform(get("/api/v1/merchants/" + merchantA + "/analytics/overview")
				.header("Authorization", "Bearer " + ownerB)
				.param("from", "2026-07-01")
				.param("to", "2026-07-01"))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/merchants/" + merchantA + "/analytics/overview")
				.param("from", "2026-07-01")
				.param("to", "2026-07-01"))
				.andExpect(status().isUnauthorized());

		String apiKey = createSecretApiKey(ownerA, merchantA);
		mockMvc.perform(get("/api/v1/merchants/" + merchantA + "/analytics/overview")
				.header("Authorization", "Bearer " + apiKey)
				.param("from", "2026-07-01")
				.param("to", "2026-07-01"))
				.andExpect(status().isForbidden());
	}

	@Test
	void defaultWindowIsThirtyInclusiveUtcDays() throws Exception {
		String token = registerLogin("analytics-default-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		MvcResult result = mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/analytics/daily")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
		LocalDate from = LocalDate.parse(body.get("fromInclusive").asText());
		LocalDate to = LocalDate.parse(body.get("toInclusive").asText());
		assertThat(java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1)
				.isEqualTo(MerchantAnalyticsService.DEFAULT_INCLUSIVE_DAYS);
		assertThat(body.get("days").size()).isEqualTo(MerchantAnalyticsService.DEFAULT_INCLUSIVE_DAYS);
		assertThat(body.get("reportingTimezone").asText()).isEqualTo("UTC");
	}

	private UUID insertPayment(String merchantId, String status, long amountMinor, Instant createdAt) {
		UUID id = UUID.randomUUID();
		Instant now = createdAt;
		String failureCode = "FAILED".equals(status) ? "TEST_DECLINED" : null;
		jdbcTemplate.update(
				"""
						INSERT INTO payments (
						  id, merchant_id, merchant_reference, amount_minor, currency, status,
						  failure_code, created_at, updated_at, version)
						VALUES (?, ?, ?, ?, 'INR', ?, ?, ?, ?, 0)
						""",
				id,
				UUID.fromString(merchantId),
				"ref-" + id.toString().substring(0, 8),
				amountMinor,
				status,
				failureCode,
				java.sql.Timestamp.from(now),
				java.sql.Timestamp.from(now));
		return id;
	}

	private void markCaptured(UUID paymentId, Instant capturedAt, long amountMinor) {
		jdbcTemplate.update(
				"""
						UPDATE payments
						SET status = 'CAPTURED', captured_at = ?, updated_at = ?, amount_minor = ?
						WHERE id = ?
						""",
				java.sql.Timestamp.from(capturedAt),
				java.sql.Timestamp.from(capturedAt),
				amountMinor,
				paymentId);
	}

	private void insertRefund(
			String merchantId, UUID paymentId, long amountMinor, String status, Instant completedAt) {
		UUID id = UUID.randomUUID();
		Instant created = completedAt != null ? completedAt.minusSeconds(60) : Instant.parse("2026-03-13T08:00:00Z");
		jdbcTemplate.update(
				"""
						INSERT INTO refunds (
						  id, payment_id, merchant_id, merchant_reference, amount_minor, currency,
						  status, created_at, updated_at, completed_at, version)
						VALUES (?, ?, ?, ?, ?, 'INR', ?, ?, ?, ?, 0)
						""",
				id,
				paymentId,
				UUID.fromString(merchantId),
				"rref-" + id.toString().substring(0, 8),
				amountMinor,
				status,
				java.sql.Timestamp.from(created),
				java.sql.Timestamp.from(completedAt != null ? completedAt : created),
				completedAt == null ? null : java.sql.Timestamp.from(completedAt));
	}

	private void insertRiskAssessment(String merchantId, UUID paymentId, String decision, Instant evaluatedAt) {
		jdbcTemplate.update(
				"""
						INSERT INTO risk_assessments (
						  id, payment_id, merchant_id, decision, risk_score, evaluated_at,
						  reason_codes, configuration_version, created_at)
						VALUES (?, ?, ?, ?, 50, ?, CAST('[]' AS jsonb), 'test-v1', ?)
						""",
				UUID.randomUUID(),
				paymentId,
				UUID.fromString(merchantId),
				decision,
				java.sql.Timestamp.from(evaluatedAt),
				java.sql.Timestamp.from(evaluatedAt));
	}

	private String createSecretApiKey(String token, String merchantId) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Analytics key","type":"SECRET"}
						"""))
				.andExpect(status().isCreated())
				.andReturn();
		return jsonMapper.readTree(created.getResponse().getContentAsString()).get("secret").asText();
	}

	private String createMerchant(String accessToken) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Analytics Merchant","description":"analytics test"}
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
