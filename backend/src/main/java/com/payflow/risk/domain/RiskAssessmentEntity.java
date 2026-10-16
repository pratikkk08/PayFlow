package com.payflow.risk.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Immutable evaluation record for a payment. Later edits to {@link RiskRuleEntity} rows
 * must not rewrite historical assessments; use {@code configurationVersion} and denormalized
 * {@link RiskRuleMatchEntity} fields for auditability.
 *
 * <p>Risk score calculation is not implemented yet (Phase 6.1 is schema only).
 */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessmentEntity {

	@Id
	private UUID id;

	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	@Column(name = "merchant_id", nullable = false)
	private UUID merchantId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private RiskDecision decision;

	@Column(name = "risk_score", nullable = false)
	private int riskScore;

	@Column(name = "evaluated_at", nullable = false)
	private Instant evaluatedAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "reason_codes", columnDefinition = "jsonb")
	private List<String> reasonCodes;

	@Column(name = "configuration_version", nullable = false, length = 64)
	private String configurationVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected RiskAssessmentEntity() {
	}

	public RiskAssessmentEntity(UUID id, UUID paymentId, UUID merchantId, RiskDecision decision,
			int riskScore, Instant evaluatedAt, List<String> reasonCodes, String configurationVersion,
			Instant createdAt) {
		this.id = id;
		this.paymentId = paymentId;
		this.merchantId = merchantId;
		this.decision = decision;
		this.riskScore = riskScore;
		this.evaluatedAt = evaluatedAt;
		this.reasonCodes = reasonCodes == null ? null : List.copyOf(reasonCodes);
		this.configurationVersion = configurationVersion;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public RiskDecision getDecision() {
		return decision;
	}

	public int getRiskScore() {
		return riskScore;
	}

	public Instant getEvaluatedAt() {
		return evaluatedAt;
	}

	public List<String> getReasonCodes() {
		return reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
	}

	public String getConfigurationVersion() {
		return configurationVersion;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
