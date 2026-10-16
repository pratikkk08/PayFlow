package com.payflow.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only ledger line. No public mutators — posting services construct new rows only.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

	@Id
	private UUID id;

	@Column(name = "ledger_transaction_id", nullable = false)
	private UUID ledgerTransactionId;

	@Column(name = "ledger_account_id", nullable = false)
	private UUID ledgerAccountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private LedgerEntryDirection direction;

	@Column(name = "amount_minor", nullable = false)
	private long amountMinor;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LedgerEntryEntity() {
	}

	public LedgerEntryEntity(UUID id, UUID ledgerTransactionId, UUID ledgerAccountId,
			LedgerEntryDirection direction, long amountMinor, Instant createdAt) {
		this.id = id;
		this.ledgerTransactionId = ledgerTransactionId;
		this.ledgerAccountId = ledgerAccountId;
		this.direction = direction;
		this.amountMinor = amountMinor;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLedgerTransactionId() {
		return ledgerTransactionId;
	}

	public UUID getLedgerAccountId() {
		return ledgerAccountId;
	}

	public LedgerEntryDirection getDirection() {
		return direction;
	}

	public long getAmountMinor() {
		return amountMinor;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
