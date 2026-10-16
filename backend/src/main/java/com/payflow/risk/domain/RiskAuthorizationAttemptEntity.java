package com.payflow.risk.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One durable authorize-risk attempt per payment. Used for PAYMENT_VELOCITY windows
 * under merchant-scoped serialization (Task 6.4).
 */
@Entity
@Table(name = "risk_authorization_attempts")
public class RiskAuthorizationAttemptEntity {

	@Id
	private UUID id;

	@Column(name = "merchant_id", nullable = false)
	private UUID merchantId;

	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	@Column(name = "attempted_at", nullable = false)
	private Instant attemptedAt;

	protected RiskAuthorizationAttemptEntity() {
	}

	public RiskAuthorizationAttemptEntity(UUID id, UUID merchantId, UUID paymentId, Instant attemptedAt) {
		this.id = id;
		this.merchantId = merchantId;
		this.paymentId = paymentId;
		this.attemptedAt = attemptedAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public Instant getAttemptedAt() {
		return attemptedAt;
	}
}
