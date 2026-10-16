package com.payflow.risk.service;

import java.util.UUID;

import com.payflow.risk.domain.RiskRuleAction;
import com.payflow.risk.domain.RiskRuleType;

/**
 * One matched rule from a read-only evaluation. Fields are shaped for later
 * persistence into {@code risk_rule_matches} (Task 6.3) without rewriting history.
 */
public record RiskMatchedRule(
		UUID ruleId,
		RiskRuleType ruleType,
		RiskRuleAction matchedAction,
		String reasonCode,
		int scoreContribution) {
}
