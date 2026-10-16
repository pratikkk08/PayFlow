package com.payflow.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

/**
 * Task 6.7: accuracy, security and query-plan hardening for merchant analytics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MerchantAnalyticsHardeningTest {

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
	void captureAndRefundOnDifferentUtcDays_andCrossMonthRefund() throws Exception {
		String token = registerLogin("hard-cross-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		UUID payment = insertPayment(merchantId, "CREATED", 20_000, Instant.parse("2026-01-15T10:00:00Z"));
		markCaptured(payment, Instant.parse("2026-01-20T12:00:00Z"), 20_000);
		insertRefund(merchantId, payment, 4_000, "SUCCEEDED", Instant.parse("2026-02-05T09:00:00Z"));
		insertRefund(merchantId, payment, 6_000, "SUCCEEDED", Instant.parse("2026-02-10T09:00:00Z"));
		insertRefund(merchantId, payment, 1_000, "FAILED", Instant.parse("2026-02-11T09:00:00Z"));
		insertRefund(merchantId, payment, 500, "PENDING", null);

		// January: capture only
		mockMvc.perform(overview(merchantId, token, "2026-01-01", "2026-01-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCapturedAmountMinor").value(20_000))
				.andExpect(jsonPath("$.totalSucceededRefundAmountMinor").value(0))
				.andExpect(jsonPath("$.netCapturedAmountMinor").value(20_000))
				.andExpect(jsonPath("$.capturedCount").value(1));

		// February: refunds of earlier capture; net negative; payment still CAPTURED
		mockMvc.perform(overview(merchantId, token, "2026-02-01", "2026-02-28"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCapturedAmountMinor").value(0))
				.andExpect(jsonPath("$.totalSucceededRefundAmountMinor").value(10_000))
				.andExpect(jsonPath("$.netCapturedAmountMinor").value(-10_000))
				.andExpect(jsonPath("$.capturedCount").value(0));

		assertThat(jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, payment)).isEqualTo("CAPTURED");

		MvcResult daily = mockMvc.perform(daily(merchantId, token, "2026-02-05", "2026-02-10"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode days = jsonMapper.readTree(daily.getResponse().getContentAsString()).get("days");
		long refundSum = 0;
		for (JsonNode day : days) {
			refundSum += day.get("succeededRefundAmountMinor").asLong();
		}
		assertThat(refundSum).isEqualTo(10_000);
	}

	@Test
	void multiplePartialRefunds_excludedFailedAndPending_reconcileDailyWithOverview() throws Exception {
		String token = registerLogin("hard-recon-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		UUID p1 = insertPayment(merchantId, "CREATED", 100_000, Instant.parse("2026-03-01T08:00:00Z"));
		markCaptured(p1, Instant.parse("2026-03-01T12:00:00Z"), 100_000);
		UUID p2 = insertPayment(merchantId, "CREATED", 50_000, Instant.parse("2026-03-02T08:00:00Z"));
		markCaptured(p2, Instant.parse("2026-03-02T18:00:00Z"), 50_000);

		insertRefund(merchantId, p1, 10_000, "SUCCEEDED", Instant.parse("2026-03-03T10:00:00Z"));
		insertRefund(merchantId, p1, 15_000, "SUCCEEDED", Instant.parse("2026-03-04T10:00:00Z"));
		insertRefund(merchantId, p2, 5_000, "SUCCEEDED", Instant.parse("2026-03-04T11:00:00Z"));
		insertRefund(merchantId, p1, 9_000, "FAILED", Instant.parse("2026-03-04T12:00:00Z"));
		insertRefund(merchantId, p2, 2_000, "PENDING", null);

		String from = "2026-03-01";
		String to = "2026-03-04";
		MvcResult overview = mockMvc.perform(overview(merchantId, token, from, to))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCapturedAmountMinor").value(150_000))
				.andExpect(jsonPath("$.totalSucceededRefundAmountMinor").value(30_000))
				.andExpect(jsonPath("$.netCapturedAmountMinor").value(120_000))
				.andReturn();

		MvcResult dailyResult = mockMvc.perform(daily(merchantId, token, from, to))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days.length()").value(4))
				.andReturn();

		JsonNode overviewBody = jsonMapper.readTree(overview.getResponse().getContentAsString());
		JsonNode days = jsonMapper.readTree(dailyResult.getResponse().getContentAsString()).get("days");
		long cap = 0;
		long ref = 0;
		long net = 0;
		for (JsonNode day : days) {
			cap += day.get("capturedAmountMinor").asLong();
			ref += day.get("succeededRefundAmountMinor").asLong();
			net += day.get("netAmountMinor").asLong();
		}
		assertThat(cap).isEqualTo(overviewBody.get("totalCapturedAmountMinor").asLong());
		assertThat(ref).isEqualTo(overviewBody.get("totalSucceededRefundAmountMinor").asLong());
		assertThat(net).isEqualTo(overviewBody.get("netCapturedAmountMinor").asLong());
	}

	@Test
	void midnightHalfOpenBoundary_andLeapDaySeries() throws Exception {
		String token = registerLogin("hard-bound-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		UUID before = insertPayment(merchantId, "CREATED", 1_000, Instant.parse("2024-02-28T23:59:59Z"));
		markCaptured(before, Instant.parse("2024-02-28T23:59:59Z"), 1_000);
		UUID onLeap = insertPayment(merchantId, "CREATED", 2_000, Instant.parse("2024-02-29T12:00:00Z"));
		markCaptured(onLeap, Instant.parse("2024-02-29T12:00:00Z"), 2_000);
		UUID atExclusive = insertPayment(merchantId, "CREATED", 3_000, Instant.parse("2024-03-01T00:00:00Z"));
		markCaptured(atExclusive, Instant.parse("2024-03-01T00:00:00Z"), 3_000);

		mockMvc.perform(overview(merchantId, token, "2024-02-28", "2024-02-29"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCapturedAmountMinor").value(3_000))
				.andExpect(jsonPath("$.rangeEndExclusive").value("2024-03-01T00:00:00Z"));

		MvcResult dailyResult = mockMvc.perform(daily(merchantId, token, "2024-02-28", "2024-03-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days.length()").value(3))
				.andReturn();
		JsonNode days = jsonMapper.readTree(dailyResult.getResponse().getContentAsString()).get("days");
		assertThat(days.get(0).get("date").asText()).isEqualTo("2024-02-28");
		assertThat(days.get(0).get("capturedAmountMinor").asLong()).isEqualTo(1_000);
		assertThat(days.get(1).get("date").asText()).isEqualTo("2024-02-29");
		assertThat(days.get(1).get("capturedAmountMinor").asLong()).isEqualTo(2_000);
		assertThat(days.get(2).get("date").asText()).isEqualTo("2024-03-01");
		assertThat(days.get(2).get("capturedAmountMinor").asLong()).isEqualTo(3_000);
	}

	@Test
	void largeMinorUnits_noOverflow_andMaxRangeBounds() throws Exception {
		String token = registerLogin("hard-large-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		long large = 50_000_000_000_000L; // 5e13 paise — well under Long.MAX_VALUE
		UUID p = insertPayment(merchantId, "CREATED", large, Instant.parse("2026-08-01T10:00:00Z"));
		markCaptured(p, Instant.parse("2026-08-01T11:00:00Z"), large);
		insertRefund(merchantId, p, 1_000_000_000_000L, "SUCCEEDED", Instant.parse("2026-08-02T10:00:00Z"));

		mockMvc.perform(overview(merchantId, token, "2026-08-01", "2026-08-02"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCapturedAmountMinor").value(large))
				.andExpect(jsonPath("$.totalSucceededRefundAmountMinor").value(1_000_000_000_000L))
				.andExpect(jsonPath("$.netCapturedAmountMinor").value(49_000_000_000_000L));

		// Leap year span = 366 inclusive days → allowed
		mockMvc.perform(overview(merchantId, token, "2024-01-01", "2024-12-31"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fromInclusive").value("2024-01-01"))
				.andExpect(jsonPath("$.toInclusive").value("2024-12-31"));

		// 367 inclusive days → rejected
		mockMvc.perform(overview(merchantId, token, "2024-01-01", "2025-01-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_reporting_range"));
	}

	@Test
	void security_malformedExpiredJwt_andIsolation_noCrossMerchantLeak() throws Exception {
		String ownerA = registerLogin("hard-sec-a-" + UUID.randomUUID() + "@example.com");
		String ownerB = registerLogin("hard-sec-b-" + UUID.randomUUID() + "@example.com");
		String merchantA = createMerchant(ownerA);
		String merchantB = createMerchant(ownerB);

		UUID secretPayment = insertPayment(merchantA, "CREATED", 777_777, Instant.parse("2026-09-01T10:00:00Z"));
		markCaptured(secretPayment, Instant.parse("2026-09-01T11:00:00Z"), 777_777);

		mockMvc.perform(overview(merchantA, ownerB, "2026-09-01", "2026-09-01"))
				.andExpect(status().isNotFound());

		MvcResult bOverview = mockMvc.perform(overview(merchantB, ownerB, "2026-09-01", "2026-09-01"))
				.andExpect(status().isOk())
				.andReturn();
		String bodyB = bOverview.getResponse().getContentAsString();
		assertThat(bodyB).doesNotContain("777777");
		assertThat(bodyB).doesNotContain(secretPayment.toString());
		assertThat(bodyB).doesNotContain(merchantA);

		mockMvc.perform(get("/api/v1/merchants/" + merchantA + "/analytics/overview")
				.header("Authorization", "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJmYWtlIn0.sig")
				.param("from", "2026-09-01")
				.param("to", "2026-09-01"))
				.andExpect(status().isUnauthorized());

		String expired = encodeToken(UUID.randomUUID(), Instant.now().minusSeconds(120),
				securityProperties.getJwt().getIssuer(), securityProperties.getJwt().getAudience());
		mockMvc.perform(get("/api/v1/merchants/" + merchantA + "/analytics/daily")
				.header("Authorization", "Bearer " + expired)
				.param("from", "2026-09-01")
				.param("to", "2026-09-01"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/merchants/" + merchantA + "/analytics/overview")
				.header("Authorization", "Bearer " + ownerA)
				.param("from", "not-a-date")
				.param("to", "2026-09-01"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void emptyPeriod_zeroDenominator_andFlagRiskNotCounted() throws Exception {
		String token = registerLogin("hard-empty-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);

		UUID flagged = insertPayment(merchantId, "AUTHORIZED", 1_000, Instant.parse("2026-10-01T10:00:00Z"));
		insertRiskAssessment(merchantId, flagged, "FLAG", Instant.parse("2026-10-01T10:05:00Z"));
		UUID blocked = insertPayment(merchantId, "FAILED", 2_000, Instant.parse("2026-10-01T11:00:00Z"));
		insertRiskAssessment(merchantId, blocked, "BLOCK", Instant.parse("2026-10-01T11:05:00Z"));

		MvcResult overview = mockMvc.perform(overview(merchantId, token, "2026-10-01", "2026-10-01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.riskBlockCount").value(1))
				.andExpect(jsonPath("$.riskReviewCount").value(0))
				.andExpect(jsonPath("$.totalCapturedAmountMinor").value(0))
				.andReturn();
		JsonNode body = jsonMapper.readTree(overview.getResponse().getContentAsString());
		// capture rate uses status CAPTURED / created-in-window; AUTHORIZED+FAILED ⇒ rate 0 not null
		assertThat(body.get("paymentCaptureRate").decimalValue()).isEqualByComparingTo("0.000000");

		mockMvc.perform(overview(merchantId, token, "2026-11-01", "2026-11-02"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalPaymentCount").value(0))
				.andExpect(jsonPath("$.riskBlockCount").value(0));
	}

	@Test
	void explainAnalyze_eventAggregatesUseBoundedIndexes() throws Exception {
		String token = registerLogin("hard-plan-" + UUID.randomUUID() + "@example.com");
		String merchantId = createMerchant(token);
		UUID mid = UUID.fromString(merchantId);

		// Spread 1_200 captures across ~3 years so a 7-day window is selective.
		Instant base = Instant.parse("2024-01-01T12:00:00Z");
		for (int i = 0; i < 1_200; i++) {
			UUID id = UUID.randomUUID();
			Instant created = base.plusSeconds(i * 86_400L);
			jdbcTemplate.update(
					"""
							INSERT INTO payments (
							  id, merchant_id, merchant_reference, amount_minor, currency, status,
							  captured_at, created_at, updated_at, version)
							VALUES (?, ?, ?, ?, 'INR', 'CAPTURED', ?, ?, ?, 0)
							""",
					id, mid, "plan-" + i, 1000L + i,
					java.sql.Timestamp.from(created.plusSeconds(60)),
					java.sql.Timestamp.from(created),
					java.sql.Timestamp.from(created.plusSeconds(60)));
			if (i % 3 == 0) {
				jdbcTemplate.update(
						"""
								INSERT INTO refunds (
								  id, payment_id, merchant_id, merchant_reference, amount_minor, currency,
								  status, created_at, updated_at, completed_at, version)
								VALUES (?, ?, ?, ?, ?, 'INR', 'SUCCEEDED', ?, ?, ?, 0)
								""",
						UUID.randomUUID(), id, mid, "rplan-" + i, 100L,
						java.sql.Timestamp.from(created.plusSeconds(120)),
						java.sql.Timestamp.from(created.plusSeconds(180)),
						java.sql.Timestamp.from(created.plusSeconds(180)));
			}
		}
		jdbcTemplate.execute("ANALYZE payments");
		jdbcTemplate.execute("ANALYZE refunds");

		LocalDate from = LocalDate.parse("2025-06-01");
		LocalDate to = LocalDate.parse("2025-06-07");
		java.sql.Timestamp start = java.sql.Timestamp.from(from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
		java.sql.Timestamp end = java.sql.Timestamp.from(
				to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());

		String capturedSql = """
				SELECT COALESCE(SUM(amount_minor), 0)::bigint AS total
				FROM payments
				WHERE merchant_id = ?
				  AND status = 'CAPTURED'
				  AND captured_at >= ?
				  AND captured_at < ?
				""";
		String refundSql = """
				SELECT COALESCE(SUM(amount_minor), 0)::bigint AS total
				FROM refunds
				WHERE merchant_id = ?
				  AND status = 'SUCCEEDED'
				  AND completed_at >= ?
				  AND completed_at < ?
				""";
		String statusSql = """
				SELECT status, COUNT(*) AS cnt
				FROM payments
				WHERE merchant_id = ?
				  AND created_at >= ?
				  AND created_at < ?
				GROUP BY status
				""";

		String capturedPlan = explain(capturedSql, mid, start, end);
		String refundPlan = explain(refundSql, mid, start, end);
		String statusPlan = explain(statusSql, mid, start, end);

		// Narrow window over multi-year history: expect V19 date indexes (or Index Scan).
		assertThat(capturedPlan).containsAnyOf(
				"idx_payments_merchant_captured_at", "Index Scan", "Bitmap Index Scan");
		assertThat(capturedPlan).contains("captured_at");
		assertThat(refundPlan).containsAnyOf(
				"idx_refunds_merchant_succeeded_completed", "Index Scan", "Bitmap Index Scan");
		assertThat(refundPlan).contains("completed_at");
		assertThat(statusPlan).containsAnyOf("Index Scan", "Bitmap Index Scan", "Seq Scan");
		assertThat(statusPlan).contains("created_at");

		double capturedMs = extractExecutionMs(capturedPlan);
		double refundMs = extractExecutionMs(refundPlan);
		assertThat(capturedMs).isLessThan(250.0);
		assertThat(refundMs).isLessThan(250.0);

		// When seq scans are forced off, V19 indexes remain selectable.
		jdbcTemplate.execute("SET enable_seqscan = off");
		try {
			String capturedIndexed = explain(capturedSql, mid, start, end);
			String refundIndexed = explain(refundSql, mid, start, end);
			assertThat(capturedIndexed).contains("idx_payments_merchant_captured_at");
			assertThat(refundIndexed).contains("idx_refunds_merchant_succeeded_completed");
		} finally {
			jdbcTemplate.execute("SET enable_seqscan = on");
		}

		MvcResult overviewResult = mockMvc.perform(overview(merchantId, token, "2025-06-01", "2025-06-07"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(jsonMapper.readTree(overviewResult.getResponse().getContentAsString())
				.get("totalCapturedAmountMinor").asLong()).isGreaterThan(0);
	}

	private String explain(String sql, Object... args) {
		String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql;
		List<String> lines = jdbcTemplate.query(explainSql, (rs, row) -> rs.getString(1), args);
		return String.join("\n", lines);
	}

	private static double extractExecutionMs(String plan) {
		// "Execution Time: 0.123 ms"
		for (String line : plan.split("\n")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("Execution Time:")) {
				String num = trimmed.substring("Execution Time:".length()).replace("ms", "").trim();
				return Double.parseDouble(num);
			}
		}
		return Double.MAX_VALUE;
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder overview(
			String merchantId, String token, String from, String to) {
		return get("/api/v1/merchants/" + merchantId + "/analytics/overview")
				.header("Authorization", "Bearer " + token)
				.param("from", from)
				.param("to", to);
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder daily(
			String merchantId, String token, String from, String to) {
		return get("/api/v1/merchants/" + merchantId + "/analytics/daily")
				.header("Authorization", "Bearer " + token)
				.param("from", from)
				.param("to", to);
	}

	private UUID insertPayment(String merchantId, String status, long amountMinor, Instant createdAt) {
		UUID id = UUID.randomUUID();
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
				java.sql.Timestamp.from(createdAt),
				java.sql.Timestamp.from(createdAt));
		return id;
	}

	private void markCaptured(UUID paymentId, Instant capturedAt, long amountMinor) {
		jdbcTemplate.update(
				"""
						UPDATE payments
						SET status = 'CAPTURED', captured_at = ?, updated_at = ?, amount_minor = ?,
						    failure_code = NULL
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
		Instant created = completedAt != null ? completedAt.minusSeconds(60) : Instant.parse("2026-02-01T08:00:00Z");
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
						VALUES (?, ?, ?, ?, 40, ?, CAST('[]' AS jsonb), 'test-v1', ?)
						""",
				UUID.randomUUID(),
				paymentId,
				UUID.fromString(merchantId),
				decision,
				java.sql.Timestamp.from(evaluatedAt),
				java.sql.Timestamp.from(evaluatedAt));
	}

	private String encodeToken(UUID subject, Instant expires, String issuer, String audience) {
		Instant issuedAt = expires.minusSeconds(60);
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

	private String createMerchant(String accessToken) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/merchants")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Hardening Merchant","description":"analytics hardening"}
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
