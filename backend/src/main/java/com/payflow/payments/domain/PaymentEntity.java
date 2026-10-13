package com.payflow.payments.domain;

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
@Table(name = "payments")
public class PaymentEntity {

	@Id
	private UUID id;

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
	private PaymentStatus status;

	@Column(length = 500)
	private String description;

	@Column(name = "failure_code", length = 64)
	private String failureCode;

	@Column(name = "authorized_at")
	private Instant authorizedAt;

	@Column(name = "captured_at")
	private Instant capturedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "processor_ref", length = 64)
	private String processorRef;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected PaymentEntity() {
	}

	public PaymentEntity(UUID id, UUID merchantId, String merchantReference, long amountMinor, String currency,
			PaymentStatus status, String description, Instant createdAt) {
		this.id = id;
		this.merchantId = merchantId;
		this.merchantReference = merchantReference;
		this.amountMinor = amountMinor;
		this.currency = currency;
		this.status = status;
		this.description = description;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public void applyAuthorizationApproved(String processorRef, Instant now) {
		requireCreatedForAuthorization();
		this.status = PaymentStatus.AUTHORIZED;
		this.processorRef = processorRef;
		this.failureCode = null;
		if (this.authorizedAt == null) {
			this.authorizedAt = now;
		}
		touch(now);
	}

	public void applyAuthorizationDeclined(String failureCode, Instant now) {
		requireCreatedForAuthorization();
		this.status = PaymentStatus.FAILED;
		this.failureCode = failureCode;
		this.processorRef = null;
		this.authorizedAt = null;
		touch(now);
	}

	public void applyCapture(Instant now) {
		if (status != PaymentStatus.AUTHORIZED) {
			throw new IllegalStateException("Payment status " + status + " cannot be captured");
		}
		this.status = PaymentStatus.CAPTURED;
		if (this.capturedAt == null) {
			this.capturedAt = now;
		}
		touch(now);
	}

	public void applyCancel(Instant now) {
		if (status != PaymentStatus.CREATED && status != PaymentStatus.AUTHORIZED) {
			throw new IllegalStateException("Payment status " + status + " cannot be cancelled");
		}
		this.status = PaymentStatus.CANCELLED;
		if (this.cancelledAt == null) {
			this.cancelledAt = now;
		}
		touch(now);
	}

	private void requireCreatedForAuthorization() {
		if (status != PaymentStatus.CREATED) {
			throw new IllegalStateException("Payment status " + status + " cannot be authorized");
		}
	}

	public UUID getId() {
		return id;
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

	public PaymentStatus getStatus() {
		return status;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public Instant getAuthorizedAt() {
		return authorizedAt;
	}

	public Instant getCapturedAt() {
		return capturedAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public String getProcessorRef() {
		return processorRef;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void touch(Instant now) {
		this.updatedAt = now;
	}

	public long getVersion() {
		return version;
	}
}
