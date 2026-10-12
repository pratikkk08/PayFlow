package com.payflow.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_token_families")
public class RefreshTokenFamilyEntity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoke_reason", length = 64)
	private String revokeReason;

	protected RefreshTokenFamilyEntity() {
	}

	public RefreshTokenFamilyEntity(UUID id, UUID userId, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public String getRevokeReason() {
		return revokeReason;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public void revoke(Instant at, String reason) {
		this.revokedAt = at;
		this.revokeReason = reason;
	}
}
