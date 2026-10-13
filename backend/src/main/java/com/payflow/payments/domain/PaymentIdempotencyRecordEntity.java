package com.payflow.payments.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_idempotency_records")
public class PaymentIdempotencyRecordEntity {

	public static final String OPERATION_CREATE_PAYMENT = "CREATE_PAYMENT";

	@Id
	private UUID id;

	@Column(name = "merchant_id", nullable = false)
	private UUID merchantId;

	@Column(nullable = false, length = 64)
	private String operation;

	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;

	@Column(name = "request_fingerprint", nullable = false, length = 64)
	private String requestFingerprint;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private IdempotencyRecordStatus status;

	@Column(name = "payment_id")
	private UUID paymentId;

	@Column(name = "http_status")
	private Integer httpStatus;

	@Column(name = "response_json")
	private String responseJson;

	@Column(name = "locked_at", nullable = false)
	private Instant lockedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PaymentIdempotencyRecordEntity() {
	}

	public PaymentIdempotencyRecordEntity(UUID id, UUID merchantId, String operation, String idempotencyKey,
			String requestFingerprint, Instant now, Instant expiresAt) {
		this.id = id;
		this.merchantId = merchantId;
		this.operation = operation;
		this.idempotencyKey = idempotencyKey;
		this.requestFingerprint = requestFingerprint;
		this.status = IdempotencyRecordStatus.STARTED;
		this.lockedAt = now;
		this.expiresAt = expiresAt;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void markCompleted(UUID paymentId, int httpStatus, String responseJson, Instant now) {
		this.status = IdempotencyRecordStatus.COMPLETED;
		this.paymentId = paymentId;
		this.httpStatus = httpStatus;
		this.responseJson = responseJson;
		this.updatedAt = now;
	}

	public void reclaimStarted(String requestFingerprint, Instant now, Instant expiresAt) {
		this.status = IdempotencyRecordStatus.STARTED;
		this.requestFingerprint = requestFingerprint;
		this.paymentId = null;
		this.httpStatus = null;
		this.responseJson = null;
		this.lockedAt = now;
		this.expiresAt = expiresAt;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public String getOperation() {
		return operation;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestFingerprint() {
		return requestFingerprint;
	}

	public IdempotencyRecordStatus getStatus() {
		return status;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public Integer getHttpStatus() {
		return httpStatus;
	}

	public String getResponseJson() {
		return responseJson;
	}

	public Instant getLockedAt() {
		return lockedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
