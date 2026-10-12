package com.payflow.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

	@Id
	private UUID id;

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "replaced_by_token_id")
	private UUID replacedByTokenId;

	protected RefreshTokenEntity() {
	}

	public RefreshTokenEntity(UUID id, UUID familyId, UUID userId, String tokenHash, Instant expiresAt,
			Instant createdAt) {
		this.id = id;
		this.familyId = familyId;
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getFamilyId() {
		return familyId;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

	public UUID getReplacedByTokenId() {
		return replacedByTokenId;
	}

	public boolean isConsumed() {
		return consumedAt != null;
	}

	public boolean isExpired(Instant now) {
		return expiresAt.isBefore(now) || expiresAt.equals(now);
	}

	public void consume(Instant at, UUID replacedBy) {
		this.consumedAt = at;
		this.replacedByTokenId = replacedBy;
	}
}
