package com.payflow.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.payflow.TestcontainersConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RiskSchemaTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void riskTablesExistWithExpectedConstraintsAndIndexes() {
		assertThat(tableCount("risk_rules")).isEqualTo(1);
		assertThat(tableCount("risk_assessments")).isEqualTo(1);
		assertThat(tableCount("risk_rule_matches")).isEqualTo(1);

		assertThat(checkConstraint("ck_risk_rules_type")).isEqualTo(1);
		assertThat(checkConstraint("ck_risk_rules_action")).isEqualTo(1);
		assertThat(checkConstraint("ck_risk_rules_priority")).isEqualTo(1);
		assertThat(checkConstraint("ck_risk_rules_parameters_object")).isEqualTo(1);

		assertThat(checkConstraint("ck_risk_assessments_decision")).isEqualTo(1);
		assertThat(checkConstraint("ck_risk_assessments_score")).isEqualTo(1);
		assertThat(checkConstraint("ck_risk_assessments_reason_codes_array")).isEqualTo(1);

		assertThat(checkConstraint("ck_risk_rule_matches_rule_type")).isEqualTo(1);
		assertThat(checkConstraint("ck_risk_rule_matches_action")).isEqualTo(1);
		assertThat(checkConstraint("ck_risk_rule_matches_score_contribution")).isEqualTo(1);

		assertThat(uniqueConstraint("uq_risk_assessments_payment")).isEqualTo(1);
		assertThat(uniqueConstraint("uq_risk_rule_matches_assessment_rule")).isEqualTo(1);
		assertThat(uniqueConstraint("uq_payments_id_merchant")).isEqualTo(1);

		assertThat(indexExists("idx_risk_rules_merchant_priority")).isEqualTo(1);
		assertThat(indexExists("idx_risk_rules_merchant_enabled")).isEqualTo(1);
		assertThat(indexExists("idx_risk_assessments_merchant_evaluated")).isEqualTo(1);
		assertThat(indexExists("idx_risk_rule_matches_assessment")).isEqualTo(1);

		String paramsType = jdbcTemplate.queryForObject(
				"""
						SELECT data_type FROM information_schema.columns
						WHERE table_name = 'risk_rules' AND column_name = 'parameters'
						""",
				String.class);
		assertThat(paramsType).isEqualTo("jsonb");
	}

	@Test
	void acceptsValidRuleAssessmentAndMatchAndRejectsInvalidShapes() {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		UUID otherMerchantId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		UUID ruleId = UUID.randomUUID();
		UUID assessmentId = UUID.randomUUID();
		UUID matchId = UUID.randomUUID();
		String email = "risk-schema-" + userId + "@example.com";

		jdbcTemplate.update(
				"""
						INSERT INTO users (id, full_name, email, email_normalized, password_hash)
						VALUES (?, 'Risk Schema', ?, ?, 'hash')
						""",
				userId, email, email);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Risk Merchant', 'ACTIVE', ?)
						""",
				merchantId, userId);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Other Risk Merchant', 'ACTIVE', ?)
						""",
				otherMerchantId, userId);
		jdbcTemplate.update(
				"""
						INSERT INTO payments (id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, 'risk-ref-1', 5000, 'INR', 'CREATED')
						""",
				paymentId, merchantId);

		jdbcTemplate.update(
				"""
						INSERT INTO risk_rules (
						  id, merchant_id, rule_type, parameters, action, priority, enabled)
						VALUES (?, ?, 'AMOUNT_THRESHOLD', CAST('{"amountMinorMax":10000}' AS jsonb),
						        'FLAG', 10, TRUE)
						""",
				ruleId, merchantId);

		jdbcTemplate.update(
				"""
						INSERT INTO risk_assessments (
						  id, payment_id, merchant_id, decision, risk_score, evaluated_at,
						  reason_codes, configuration_version)
						VALUES (?, ?, ?, 'FLAG', 40, NOW(),
						        CAST('["AMOUNT_THRESHOLD"]' AS jsonb), 'snap-v1')
						""",
				assessmentId, paymentId, merchantId);

		jdbcTemplate.update(
				"""
						INSERT INTO risk_rule_matches (
						  id, assessment_id, rule_id, rule_type, matched_action,
						  reason_code, score_contribution)
						VALUES (?, ?, ?, 'AMOUNT_THRESHOLD', 'FLAG', 'AMOUNT_ABOVE_THRESHOLD', 40)
						""",
				matchId, assessmentId, ruleId);

		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM risk_rules WHERE id = ?", Integer.class, ruleId)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM risk_assessments WHERE id = ?", Integer.class, assessmentId)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM risk_rule_matches WHERE id = ?", Integer.class, matchId)).isEqualTo(1);

		// Invalid rule type
		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO risk_rules (
						  id, merchant_id, rule_type, parameters, action, priority, enabled)
						VALUES (?, ?, 'UNKNOWN_RULE', CAST('{}' AS jsonb), 'FLAG', 1, TRUE)
						""",
				UUID.randomUUID(), merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		// Invalid action
		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO risk_rules (
						  id, merchant_id, rule_type, parameters, action, priority, enabled)
						VALUES (?, ?, 'PAYMENT_VELOCITY', CAST('{}' AS jsonb), 'ALLOW', 1, TRUE)
						""",
				UUID.randomUUID(), merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		// Parameters must be a JSON object
		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO risk_rules (
						  id, merchant_id, rule_type, parameters, action, priority, enabled)
						VALUES (?, ?, 'REPEATED_FAILURES', CAST('[1]' AS jsonb), 'BLOCK', 1, TRUE)
						""",
				UUID.randomUUID(), merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		// Risk score out of range
		UUID paymentScore = UUID.randomUUID();
		jdbcTemplate.update(
				"""
						INSERT INTO payments (id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, 'risk-ref-score', 1000, 'INR', 'CREATED')
						""",
				paymentScore, merchantId);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO risk_assessments (
						  id, payment_id, merchant_id, decision, risk_score, evaluated_at,
						  configuration_version)
						VALUES (?, ?, ?, 'ALLOW', 101, NOW(), 'snap-v2')
						""",
				UUID.randomUUID(), paymentScore, merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		// Merchant must match payment (composite FK)
		UUID paymentMerchant = UUID.randomUUID();
		jdbcTemplate.update(
				"""
						INSERT INTO payments (id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, 'risk-ref-merchant', 1000, 'INR', 'CREATED')
						""",
				paymentMerchant, merchantId);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO risk_assessments (
						  id, payment_id, merchant_id, decision, risk_score, evaluated_at,
						  configuration_version)
						VALUES (?, ?, ?, 'ALLOW', 0, NOW(), 'snap-v3')
						""",
				UUID.randomUUID(), paymentMerchant, otherMerchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		// One assessment per payment
		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO risk_assessments (
						  id, payment_id, merchant_id, decision, risk_score, evaluated_at,
						  configuration_version)
						VALUES (?, ?, ?, 'ALLOW', 0, NOW(), 'snap-v4')
						""",
				UUID.randomUUID(), paymentId, merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		// Invalid decision
		UUID payment2 = UUID.randomUUID();
		jdbcTemplate.update(
				"""
						INSERT INTO payments (id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, 'risk-ref-2', 1000, 'INR', 'CREATED')
						""",
				payment2, merchantId);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO risk_assessments (
						  id, payment_id, merchant_id, decision, risk_score, evaluated_at,
						  configuration_version)
						VALUES (?, ?, ?, 'DECLINE', 10, NOW(), 'snap-v5')
						""",
				UUID.randomUUID(), payment2, merchantId))
				.isInstanceOf(DataIntegrityViolationException.class);

		// Duplicate match for same assessment+rule
		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO risk_rule_matches (
						  id, assessment_id, rule_id, rule_type, matched_action,
						  reason_code, score_contribution)
						VALUES (?, ?, ?, 'AMOUNT_THRESHOLD', 'FLAG', 'DUP', 10)
						""",
				UUID.randomUUID(), assessmentId, ruleId))
				.isInstanceOf(DataIntegrityViolationException.class);

		// Score contribution out of range
		UUID rule2 = UUID.randomUUID();
		jdbcTemplate.update(
				"""
						INSERT INTO risk_rules (
						  id, merchant_id, rule_type, parameters, action, priority, enabled)
						VALUES (?, ?, 'PAYMENT_VELOCITY', CAST('{"maxPerHour":5}' AS jsonb),
						        'REVIEW', 20, TRUE)
						""",
				rule2, merchantId);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO risk_rule_matches (
						  id, assessment_id, rule_id, rule_type, matched_action,
						  reason_code, score_contribution)
						VALUES (?, ?, ?, 'PAYMENT_VELOCITY', 'REVIEW', 'TOO_FAST', 150)
						""",
				UUID.randomUUID(), assessmentId, rule2))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void ruleParameterChangesDoNotRewriteHistoricalMatchSnapshot() {
		UUID userId = UUID.randomUUID();
		UUID merchantId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		UUID ruleId = UUID.randomUUID();
		UUID assessmentId = UUID.randomUUID();
		UUID matchId = UUID.randomUUID();
		String email = "risk-audit-" + userId + "@example.com";

		jdbcTemplate.update(
				"""
						INSERT INTO users (id, full_name, email, email_normalized, password_hash)
						VALUES (?, 'Risk Audit', ?, ?, 'hash')
						""",
				userId, email, email);
		jdbcTemplate.update(
				"""
						INSERT INTO merchants (id, name, status, owner_user_id)
						VALUES (?, 'Risk Audit Merchant', 'ACTIVE', ?)
						""",
				merchantId, userId);
		jdbcTemplate.update(
				"""
						INSERT INTO payments (id, merchant_id, merchant_reference, amount_minor, currency, status)
						VALUES (?, ?, 'risk-audit-1', 9000, 'INR', 'CREATED')
						""",
				paymentId, merchantId);
		jdbcTemplate.update(
				"""
						INSERT INTO risk_rules (
						  id, merchant_id, rule_type, parameters, action, priority, enabled)
						VALUES (?, ?, 'AMOUNT_THRESHOLD', CAST('{"amountMinorMax":5000}' AS jsonb),
						        'BLOCK', 1, TRUE)
						""",
				ruleId, merchantId);
		jdbcTemplate.update(
				"""
						INSERT INTO risk_assessments (
						  id, payment_id, merchant_id, decision, risk_score, evaluated_at,
						  configuration_version)
						VALUES (?, ?, ?, 'BLOCK', 90, NOW(), 'cfg-audit-1')
						""",
				assessmentId, paymentId, merchantId);
		jdbcTemplate.update(
				"""
						INSERT INTO risk_rule_matches (
						  id, assessment_id, rule_id, rule_type, matched_action,
						  reason_code, score_contribution)
						VALUES (?, ?, ?, 'AMOUNT_THRESHOLD', 'BLOCK', 'AMOUNT_ABOVE_THRESHOLD', 90)
						""",
				matchId, assessmentId, ruleId);

		jdbcTemplate.update(
				"""
						UPDATE risk_rules
						SET parameters = CAST('{"amountMinorMax":50000}' AS jsonb),
						    action = 'FLAG',
						    updated_at = NOW(),
						    version = version + 1
						WHERE id = ?
						""",
				ruleId);

		String matchedAction = jdbcTemplate.queryForObject(
				"SELECT matched_action FROM risk_rule_matches WHERE id = ?", String.class, matchId);
		Integer scoreContribution = jdbcTemplate.queryForObject(
				"SELECT score_contribution FROM risk_rule_matches WHERE id = ?", Integer.class, matchId);
		String decision = jdbcTemplate.queryForObject(
				"SELECT decision FROM risk_assessments WHERE id = ?", String.class, assessmentId);
		String liveAction = jdbcTemplate.queryForObject(
				"SELECT action FROM risk_rules WHERE id = ?", String.class, ruleId);

		assertThat(matchedAction).isEqualTo("BLOCK");
		assertThat(scoreContribution).isEqualTo(90);
		assertThat(decision).isEqualTo("BLOCK");
		assertThat(liveAction).isEqualTo("FLAG");
	}

	private Integer tableCount(String tableName) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.tables
						WHERE table_schema = 'public' AND table_name = ?
						""",
				Integer.class, tableName);
	}

	private Integer checkConstraint(String name) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.table_constraints
						WHERE constraint_schema = 'public'
						  AND constraint_name = ?
						  AND constraint_type = 'CHECK'
						""",
				Integer.class, name);
	}

	private Integer uniqueConstraint(String name) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM information_schema.table_constraints
						WHERE constraint_schema = 'public'
						  AND constraint_name = ?
						  AND constraint_type = 'UNIQUE'
						""",
				Integer.class, name);
	}

	private Integer indexExists(String name) {
		return jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM pg_indexes
						WHERE schemaname = 'public' AND indexname = ?
						""",
				Integer.class, name);
	}
}
