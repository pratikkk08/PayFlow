package com.payflow.refunds.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "refunds")
public class RefundEntity {

	@Id
	private UUID id;

	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	@Column(name = "merchant_id", nullable = false)
	private UUID merchantId;

	@Column(name = "merchant_reference", nullable = false, length = 64)
	private String merchantReference;

	@Column(name = "amount_minor", nullable = false)
	private long amountMinor;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private RefundStatus status;

	@Column(name = "failure_code", length = 64)
	private String failureCode;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Version
	private long version;

	protected RefundEntity() {
	}

	public RefundEntity(UUID id, UUID paymentId, UUID merchantId, String merchantReference, long amountMinor,
			String currency, RefundStatus status, Instant createdAt) {
		this.id = id;
		this.paymentId = paymentId;
		this.merchantId = merchantId;
		this.merchantReference = merchantReference;
		this.amountMinor = amountMinor;
		this.currency = currency;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public static RefundEntity createPending(UUID id, UUID paymentId, UUID merchantId, String merchantReference,
			long amountMinor, String currency, Instant createdAt) {
		return new RefundEntity(id, paymentId, merchantId, merchantReference, amountMinor, currency,
				RefundStatus.PENDING, createdAt);
	}

	public void applySucceeded(Instant now) {
		if (status != RefundStatus.PENDING) {
			throw new IllegalStateException("Refund status " + status + " cannot be marked succeeded");
		}
		this.status = RefundStatus.SUCCEEDED;
		this.failureCode = null;
		this.completedAt = now;
		touch(now);
	}

	public void applyFailed(String failureCode, Instant now) {
		if (status != RefundStatus.PENDING) {
			throw new IllegalStateException("Refund status " + status + " cannot be marked failed");
		}
		this.status = RefundStatus.FAILED;
		this.failureCode = failureCode;
		this.completedAt = now;
		touch(now);
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

	public String getMerchantReference() {
		return merchantReference;
	}

	public long getAmountMinor() {
		return amountMinor;
	}

	public String getCurrency() {
		return currency;
	}

	public RefundStatus getStatus() {
		return status;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public long getVersion() {
		return version;
	}

	public void touch(Instant now) {
		this.updatedAt = now;
	}
}
