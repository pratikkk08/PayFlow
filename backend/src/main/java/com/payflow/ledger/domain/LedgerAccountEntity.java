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
@Table(name = "ledger_accounts")
public class LedgerAccountEntity {

	@Id
	private UUID id;

	@Column(name = "merchant_id")
	private UUID merchantId;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_type", nullable = false, length = 32)
	private LedgerAccountType accountType;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LedgerAccountEntity() {
	}

	public LedgerAccountEntity(UUID id, UUID merchantId, LedgerAccountType accountType, String currency,
			boolean active, Instant createdAt) {
		this.id = id;
		this.merchantId = merchantId;
		this.accountType = accountType;
		this.currency = currency;
		this.active = active;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public LedgerAccountType getAccountType() {
		return accountType;
	}

	public String getCurrency() {
		return currency;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
