package com.payflow.risk.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "risk_rules")
public class RiskRuleEntity {

	@Id
	private UUID id;

	@Column(name = "merchant_id", nullable = false)
	private UUID merchantId;

	@Enumerated(EnumType.STRING)
	@Column(name = "rule_type", nullable = false, length = 64)
	private RiskRuleType ruleType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String parameters;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private RiskRuleAction action;

	@Column(nullable = false)
	private int priority;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected RiskRuleEntity() {
	}

	public RiskRuleEntity(UUID id, UUID merchantId, RiskRuleType ruleType, String parameters,
			RiskRuleAction action, int priority, boolean enabled, Instant now) {
		this.id = id;
		this.merchantId = merchantId;
		this.ruleType = ruleType;
		this.parameters = parameters;
		this.action = action;
		this.priority = priority;
		this.enabled = enabled;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public RiskRuleType getRuleType() {
		return ruleType;
	}

	public String getParameters() {
		return parameters;
	}

	public void setParameters(String parameters) {
		this.parameters = parameters;
	}

	public RiskRuleAction getAction() {
		return action;
	}

	public void setAction(RiskRuleAction action) {
		this.action = action;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}

	public void touch(Instant now) {
		this.updatedAt = now;
	}
}
