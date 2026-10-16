package com.payflow.risk.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.payflow.risk.domain.RiskDecision;

/**
 * Structured read-only risk evaluation outcome. Designed so Task 6.3 can persist
 * into {@code risk_assessments} / {@code risk_rule_matches} without re-scoring.
 *
 * <p>This task does <b>not</b> write assessments or change payment status.
 */
public record RiskEvaluationResult(
		UUID paymentId,
		UUID merchantId,
		int riskScore,
		RiskDecision decision,
		List<UUID> matchedRuleIds,
		List<String> reasonCodes,
		String configurationVersion,
		Instant evaluatedAt,
		List<RiskMatchedRule> matches) {

	public RiskEvaluationResult {
		matchedRuleIds = List.copyOf(matchedRuleIds);
		reasonCodes = List.copyOf(reasonCodes);
		matches = List.copyOf(matches);
	}
}
