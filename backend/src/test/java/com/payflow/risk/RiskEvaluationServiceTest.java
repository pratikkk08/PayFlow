package com.payflow.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.payflow.TestcontainersConfiguration;
import com.payflow.risk.domain.RiskDecision;
import com.payflow.risk.service.RiskEvaluationResult;
import com.payflow.risk.service.RiskEvaluationService;
import com.payflow.risk.service.RiskScoring;

@SpringBootTest
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, RiskEvaluationTestConfig.class })
class RiskEvaluationServiceTest {

	@Autowired
	private RiskEvaluationService riskEvaluationService;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private MutableClock mutableClock;

	@BeforeEach
	void resetClock() {
		mutableClock.setInstant(RiskEvaluationTestConfig.FIXED_NOW);
	}

	@Test
	void noRules_allowsWithScoreZero() {
		Fixture fx = seedMerchantAndPayment(5_000);

		RiskEvaluationResult result = riskEvaluationService.evaluate(fx.paymentId);

		assertThat(result.decision()).isEqualTo(RiskDecision.ALLOW);
		assertThat(result.riskScore()).isZero();
		assertThat(result.matchedRuleIds()).isEmpty();
		assertThat(result.reasonCodes()).isEmpty();
		assertThat(result.matches()).isEmpty();
		assertThat(result.configurationVersion()).isEqualTo("cfg:empty");
		assertThat(result.evaluatedAt()).isEqualTo(RiskEvaluationTestConfig.FIXED_NOW);
		assertThat(result.merchantId()).isEqualTo(fx.merchantId);
		assertThat(result.paymentId()).isEqualTo(fx.paymentId);
	}

	@Test
	void amountThreshold_strictGreaterThanBoundary() {
		Fixture fx = seedMerchantAndPayment(10_000);
		insertRule(fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":10000}", "FLAG", 10, true);

		RiskEvaluationResult atThreshold = riskEvaluationService.evaluate(fx.paymentId);
		assertThat(atThreshold.decision()).isEqualTo(RiskDecision.ALLOW);
		assertThat(atThreshold.riskScore()).isZero();

		UUID overPayment = insertPayment(fx.merchantId, "amt-over", 10_001, "CREATED",
				RiskEvaluationTestConfig.FIXED_NOW, RiskEvaluationTestConfig.FIXED_NOW);
		RiskEvaluationResult over = riskEvaluationService.evaluate(overPayment);
		assertThat(over.decision()).isEqualTo(RiskDecision.FLAG);
		assertThat(over.riskScore()).isEqualTo(RiskScoring.SCORE_FLAG);
		assertThat(over.reasonCodes()).containsExactly(RiskEvaluationService.REASON_AMOUNT_ABOVE_THRESHOLD);
	}

	@Test
	void velocity_insideAndOutsideWindow() {
		Fixture fx = seedMerchantAndPayment(1_000);
		insertRule(fx.merchantId, "PAYMENT_VELOCITY", "{\"maxCount\":2,\"windowSeconds\":3600}", "REVIEW", 10,
				true);

		Instant now = RiskEvaluationTestConfig.FIXED_NOW;
		UUID vel1 = insertPayment(fx.merchantId, "vel-1", 100, "CREATED", now.minusSeconds(600),
				now.minusSeconds(600));
		UUID vel2 = insertPayment(fx.merchantId, "vel-2", 100, "CREATED", now.minusSeconds(300),
				now.minusSeconds(300));
		insertAttempt(fx.merchantId, vel1, now.minusSeconds(600));
		insertAttempt(fx.merchantId, vel2, now.minusSeconds(300));

		RiskEvaluationResult inside = riskEvaluationService.evaluate(fx.paymentId);
		assertThat(inside.decision()).isEqualTo(RiskDecision.REVIEW);
		assertThat(inside.riskScore()).isEqualTo(RiskScoring.SCORE_REVIEW);
		assertThat(inside.reasonCodes()).containsExactly(RiskEvaluationService.REASON_PAYMENT_VELOCITY_EXCEEDED);

		Fixture fx2 = seedMerchantAndPayment(1_000);
		insertRule(fx2.merchantId, "PAYMENT_VELOCITY", "{\"maxCount\":2,\"windowSeconds\":3600}", "REVIEW", 10,
				true);
		UUID oldOnly = insertPayment(fx2.merchantId, "vel-old-only", 100, "CREATED", now.minusSeconds(10_000),
				now.minusSeconds(10_000));
		insertAttempt(fx2.merchantId, oldOnly, now.minusSeconds(10_000));

		RiskEvaluationResult outside = riskEvaluationService.evaluate(fx2.paymentId);
		assertThat(outside.decision()).isEqualTo(RiskDecision.ALLOW);
		assertThat(outside.riskScore()).isZero();
	}

	@Test
	void repeatedFailures_insideAndOutsideWindow() {
		Instant now = RiskEvaluationTestConfig.FIXED_NOW;
		Fixture fx = seedMerchantAndPayment(1_000);
		insertRule(fx.merchantId, "REPEATED_FAILURES", "{\"maxFailures\":2,\"windowSeconds\":7200}", "BLOCK", 10,
				true);

		insertFailedPayment(fx.merchantId, "fail-1", now.minusSeconds(100), now.minusSeconds(100));
		insertFailedPayment(fx.merchantId, "fail-2", now.minusSeconds(50), now.minusSeconds(50));

		RiskEvaluationResult inside = riskEvaluationService.evaluate(fx.paymentId);
		assertThat(inside.decision()).isEqualTo(RiskDecision.BLOCK);
		assertThat(inside.riskScore()).isEqualTo(RiskScoring.SCORE_BLOCK);
		assertThat(inside.reasonCodes()).containsExactly(RiskEvaluationService.REASON_REPEATED_FAILURES_EXCEEDED);

		Fixture fx2 = seedMerchantAndPayment(1_000);
		insertRule(fx2.merchantId, "REPEATED_FAILURES", "{\"maxFailures\":2,\"windowSeconds\":7200}", "BLOCK", 10,
				true);
		insertFailedPayment(fx2.merchantId, "fail-old", now.minusSeconds(20_000), now.minusSeconds(20_000));

		RiskEvaluationResult outside = riskEvaluationService.evaluate(fx2.paymentId);
		assertThat(outside.decision()).isEqualTo(RiskDecision.ALLOW);
		assertThat(outside.riskScore()).isZero();
	}

	@Test
	void disabledRule_ignored() {
		Fixture fx = seedMerchantAndPayment(50_000);
		insertRule(fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":1000}", "BLOCK", 1, false);

		RiskEvaluationResult result = riskEvaluationService.evaluate(fx.paymentId);
		assertThat(result.decision()).isEqualTo(RiskDecision.ALLOW);
		assertThat(result.matchedRuleIds()).isEmpty();
	}

	@Test
	void merchantIsolation_otherMerchantRulesDoNotApply() {
		Fixture target = seedMerchantAndPayment(50_000);
		Fixture other = seedMerchantAndPayment(50_000);
		insertRule(other.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "BLOCK", 1, true);

		RiskEvaluationResult result = riskEvaluationService.evaluate(target.paymentId);
		assertThat(result.decision()).isEqualTo(RiskDecision.ALLOW);
		assertThat(result.matchedRuleIds()).isEmpty();
	}

	@Test
	void priorityOrdering_matchesStableByPriorityThenId() {
		Fixture fx = seedMerchantAndPayment(50_000);
		UUID lowPriority = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
		UUID highPriority = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
		insertRuleWithId(highPriority, fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "FLAG", 20,
				true);
		insertRuleWithId(lowPriority, fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "REVIEW", 5,
				true);

		RiskEvaluationResult result = riskEvaluationService.evaluate(fx.paymentId);
		assertThat(result.matchedRuleIds()).containsExactly(lowPriority, highPriority);
		assertThat(result.decision()).isEqualTo(RiskDecision.REVIEW);
	}

	@Test
	void multipleMatches_scoreClampedAndDecisionPrecedence() {
		Fixture fx = seedMerchantAndPayment(50_000);
		insertRule(fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "FLAG", 1, true);
		insertRule(fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":200}", "REVIEW", 2, true);
		insertRule(fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":300}", "BLOCK", 3, true);

		RiskEvaluationResult result = riskEvaluationService.evaluate(fx.paymentId);
		assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK);
		assertThat(result.riskScore()).isEqualTo(100);
		assertThat(result.matches()).hasSize(3);
		int raw = RiskScoring.SCORE_FLAG + RiskScoring.SCORE_REVIEW + RiskScoring.SCORE_BLOCK;
		assertThat(raw).isGreaterThan(100);
		assertThat(result.riskScore()).isEqualTo(RiskScoring.clamp(raw));
	}

	@Test
	void decisionPrecedence_blockBeatsReviewBeatsFlag() {
		Fixture fx = seedMerchantAndPayment(50_000);
		insertRule(fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":1}", "FLAG", 1, true);
		insertRule(fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":2}", "BLOCK", 2, true);
		insertRule(fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":3}", "REVIEW", 3, true);

		assertThat(riskEvaluationService.evaluate(fx.paymentId).decision()).isEqualTo(RiskDecision.BLOCK);
	}

	@Test
	void malformedParameters_failClosedForBlockAndReview_skipFlag() {
		Fixture fx = seedMerchantAndPayment(1_000);
		// DB requires JSONB object; malformed = missing/invalid fields for the rule type.
		UUID badFlag = insertRule(fx.merchantId, "AMOUNT_THRESHOLD", "{\"wrong\":true}", "FLAG", 1, true);
		UUID badReview = insertRule(fx.merchantId, "PAYMENT_VELOCITY", "{\"maxCount\":-1,\"windowSeconds\":60}",
				"REVIEW", 2, true);
		UUID badBlock = insertRule(fx.merchantId, "REPEATED_FAILURES", "{\"maxFailures\":2}", "BLOCK", 3, true);

		RiskEvaluationResult result = riskEvaluationService.evaluate(fx.paymentId);
		assertThat(result.matchedRuleIds()).containsExactly(badReview, badBlock);
		assertThat(result.matchedRuleIds()).doesNotContain(badFlag);
		assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK);
		assertThat(result.reasonCodes()).containsOnly(RiskEvaluationService.REASON_RULE_CONFIG_INVALID);
		assertThat(result.riskScore()).isEqualTo(100);
	}

	@Test
	void injectedClock_isUsedForEvaluationTimestampAndWindows() {
		Fixture fx = seedMerchantAndPayment(1_000);
		insertRule(fx.merchantId, "PAYMENT_VELOCITY", "{\"maxCount\":1,\"windowSeconds\":60}", "FLAG", 1, true);

		Instant early = Instant.parse("2026-06-15T11:00:00Z");
		UUID neighbor = insertPayment(fx.merchantId, "clock-neighbor", 100, "CREATED", early, early);
		insertAttempt(fx.merchantId, neighbor, early);

		mutableClock.setInstant(Instant.parse("2026-06-15T11:00:30Z"));
		RiskEvaluationResult near = riskEvaluationService.evaluate(fx.paymentId);
		assertThat(near.decision()).isEqualTo(RiskDecision.FLAG);
		assertThat(near.evaluatedAt()).isEqualTo(Instant.parse("2026-06-15T11:00:30Z"));

		mutableClock.setInstant(Instant.parse("2026-06-15T12:00:00Z"));
		RiskEvaluationResult later = riskEvaluationService.evaluate(fx.paymentId);
		assertThat(later.decision()).isEqualTo(RiskDecision.ALLOW);
		assertThat(later.evaluatedAt()).isEqualTo(Instant.parse("2026-06-15T12:00:00Z"));
	}

	@Test
	void evaluation_isReadOnly_noAssessmentOrPaymentWrites() {
		Fixture fx = seedMerchantAndPayment(50_000);
		insertRule(fx.merchantId, "AMOUNT_THRESHOLD", "{\"thresholdMinor\":100}", "FLAG", 1, true);

		Integer assessmentsBefore = countTable("risk_assessments");
		Integer matchesBefore = countTable("risk_rule_matches");
		Long versionBefore = jdbcTemplate.queryForObject(
				"SELECT version FROM payments WHERE id = ?", Long.class, fx.paymentId);
		String statusBefore = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, fx.paymentId);

		RiskEvaluationResult result = riskEvaluationService.evaluate(fx.paymentId);
		assertThat(result.decision()).isEqualTo(RiskDecision.FLAG);

		assertThat(countTable("risk_assessments")).isEqualTo(assessmentsBefore);
		assertThat(countTable("risk_rule_matches")).isEqualTo(matchesBefore);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT version FROM payments WHERE id = ?", Long.class, fx.paymentId)).isEqualTo(versionBefore);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE id = ?", String.class, fx.paymentId)).isEqualTo(statusBefore);
	}

	private Integer countTable(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private Fixture seedMerchantAndPayment(long amountMinor) {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		String email = "risk-eval-" + userId + "@example.com";
		jdbcTemplate.update(
				"""
						INSERT INTO users (id, full_name, email, email_normalized, password_hash)
						VALUES (?, 'Risk Eval', ?, ?, 'hash')
						""",
				userId, email, email);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Risk Eval Merchant', 'ACTIVE', ?)
						""",
				merchantId, userId);
		UUID paymentId = insertPayment(merchantId, "pay-" + UUID.randomUUID().toString().substring(0, 8),
				amountMinor, "CREATED", RiskEvaluationTestConfig.FIXED_NOW, RiskEvaluationTestConfig.FIXED_NOW);
		return new Fixture(merchantId, paymentId);
	}

	private UUID insertPayment(UUID merchantId, String reference, long amountMinor, String status,
			Instant createdAt, Instant updatedAt) {
		UUID paymentId = UUID.randomUUID();
		jdbcTemplate.update(
				"""
						INSERT INTO payments (
						  id, merchant_id, merchant_reference, amount_minor, currency, status,
						  created_at, updated_at)
						VALUES (?, ?, ?, ?, 'INR', ?, ?, ?)
						""",
				paymentId, merchantId, reference, amountMinor, status, Timestamp.from(createdAt),
				Timestamp.from(updatedAt));
		return paymentId;
	}

	private void insertFailedPayment(UUID merchantId, String reference, Instant createdAt, Instant updatedAt) {
		UUID paymentId = UUID.randomUUID();
		jdbcTemplate.update(
				"""
						INSERT INTO payments (
						  id, merchant_id, merchant_reference, amount_minor, currency, status,
						  failure_code, created_at, updated_at)
						VALUES (?, ?, ?, 100, 'INR', 'FAILED', 'simulated_decline', ?, ?)
						""",
				paymentId, merchantId, reference, Timestamp.from(createdAt), Timestamp.from(updatedAt));
	}

	private void insertAttempt(UUID merchantId, UUID paymentId, Instant attemptedAt) {
		jdbcTemplate.update(
				"""
						INSERT INTO risk_authorization_attempts (id, merchant_id, payment_id, attempted_at)
						VALUES (?, ?, ?, ?)
						""",
				UUID.randomUUID(), merchantId, paymentId, Timestamp.from(attemptedAt));
	}

	private UUID insertRule(UUID merchantId, String type, String params, String action, int priority,
			boolean enabled) {
		return insertRuleWithId(UUID.randomUUID(), merchantId, type, params, action, priority, enabled);
	}

	private UUID insertRuleWithId(UUID ruleId, UUID merchantId, String type, String params, String action,
			int priority, boolean enabled) {
		jdbcTemplate.update(
				"""
						INSERT INTO risk_rules (
						  id, merchant_id, rule_type, parameters, action, priority, enabled)
						VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
						""",
				ruleId, merchantId, type, params, action, priority, enabled);
		return ruleId;
	}

	private record Fixture(UUID merchantId, UUID paymentId) {
	}
}
