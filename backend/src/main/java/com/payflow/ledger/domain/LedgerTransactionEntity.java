package com.payflow.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransactionEntity {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 32)
	private LedgerSourceType sourceType;

	@Column(name = "source_id", nullable = false)
	private UUID sourceId;

	@Column(name = "merchant_id", nullable = false)
	private UUID merchantId;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LedgerTransactionEntity() {
	}

	public LedgerTransactionEntity(UUID id, LedgerSourceType sourceType, UUID sourceId, UUID merchantId,
			String currency, Instant createdAt) {
		this.id = id;
		this.sourceType = sourceType;
		this.sourceId = sourceId;
		this.merchantId = merchantId;
		this.currency = currency;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public LedgerSourceType getSourceType() {
		return sourceType;
	}

	public UUID getSourceId() {
		return sourceId;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public String getCurrency() {
		return currency;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
