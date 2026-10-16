package com.payflow.risk.domain;

/**
 * Outcome of a payment risk assessment / evaluation.
 */
public enum RiskDecision {
	ALLOW,
	FLAG,
	REVIEW,
	BLOCK
}
