package com.payflow.risk.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Links an assessment to a matched rule. {@code ruleType}, {@code matchedAction},
 * {@code reasonCode}, and {@code scoreContribution} are denormalized so later rule
 * configuration changes do not rewrite historical match records.
 *
 * <p>Rule execution is not implemented yet (Phase 6.1 is schema only).
 */
@Entity
@Table(name = "risk_rule_matches")
public class RiskRuleMatchEntity {

	@Id
	private UUID id;

	@Column(name = "assessment_id", nullable = false)
	private UUID assessmentId;

	@Column(name = "rule_id", nullable = false)
	private UUID ruleId;

	@Enumerated(EnumType.STRING)
	@Column(name = "rule_type", nullable = false, length = 64)
	private RiskRuleType ruleType;

	@Enumerated(EnumType.STRING)
	@Column(name = "matched_action", nullable = false, length = 16)
	private RiskRuleAction matchedAction;

	@Column(name = "reason_code", length = 64)
	private String reasonCode;

	@Column(name = "score_contribution", nullable = false)
	private int scoreContribution;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected RiskRuleMatchEntity() {
	}

	public RiskRuleMatchEntity(UUID id, UUID assessmentId, UUID ruleId, RiskRuleType ruleType,
			RiskRuleAction matchedAction, String reasonCode, int scoreContribution, Instant createdAt) {
		this.id = id;
		this.assessmentId = assessmentId;
		this.ruleId = ruleId;
		this.ruleType = ruleType;
		this.matchedAction = matchedAction;
		this.reasonCode = reasonCode;
		this.scoreContribution = scoreContribution;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getAssessmentId() {
		return assessmentId;
	}

	public UUID getRuleId() {
		return ruleId;
	}

	public RiskRuleType getRuleType() {
		return ruleType;
	}

	public RiskRuleAction getMatchedAction() {
		return matchedAction;
	}

	public String getReasonCode() {
		return reasonCode;
	}

	public int getScoreContribution() {
		return scoreContribution;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
