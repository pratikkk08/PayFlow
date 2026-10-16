package com.payflow.risk.service;

import com.payflow.risk.domain.RiskDecision;
import com.payflow.risk.domain.RiskRuleAction;

/**
 * Deterministic scoring and decision precedence for simulated risk evaluation.
 *
 * <p><b>Score contributions (fixed per matched action):</b>
 * <ul>
 *   <li>{@link RiskRuleAction#FLAG} → +25</li>
 *   <li>{@link RiskRuleAction#REVIEW} → +50</li>
 *   <li>{@link RiskRuleAction#BLOCK} → +100</li>
 * </ul>
 * Contributions are summed across matched rules, then clamped to {@code [0, 100]}.
 * No matches → score {@code 0} and {@link RiskDecision#ALLOW}.
 *
 * <p><b>Decision precedence:</b> {@code BLOCK > REVIEW > FLAG > ALLOW}
 * (highest severity among matched rule actions wins).
 */
public final class RiskScoring {

	public static final int SCORE_FLAG = 25;
	public static final int SCORE_REVIEW = 50;
	public static final int SCORE_BLOCK = 100;

	private RiskScoring() {
	}

	public static int contributionFor(RiskRuleAction action) {
		return switch (action) {
			case FLAG -> SCORE_FLAG;
			case REVIEW -> SCORE_REVIEW;
			case BLOCK -> SCORE_BLOCK;
		};
	}

	public static int clamp(int rawScore) {
		if (rawScore < 0) {
			return 0;
		}
		if (rawScore > 100) {
			return 100;
		}
		return rawScore;
	}

	public static RiskDecision toDecision(RiskRuleAction action) {
		return switch (action) {
			case FLAG -> RiskDecision.FLAG;
			case REVIEW -> RiskDecision.REVIEW;
			case BLOCK -> RiskDecision.BLOCK;
		};
	}

	/**
	 * Returns the higher-severity decision (BLOCK beats REVIEW beats FLAG beats ALLOW).
	 */
	public static RiskDecision maxSeverity(RiskDecision a, RiskDecision b) {
		return severity(a) >= severity(b) ? a : b;
	}

	private static int severity(RiskDecision decision) {
		return switch (decision) {
			case ALLOW -> 0;
			case FLAG -> 1;
			case REVIEW -> 2;
			case BLOCK -> 3;
		};
	}
}
