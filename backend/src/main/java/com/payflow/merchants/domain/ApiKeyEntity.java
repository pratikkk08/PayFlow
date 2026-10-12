package com.payflow.merchants.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {

	@Id
	private UUID id;

	@Column(name = "merchant_id", nullable = false)
	private UUID merchantId;

	@Column(nullable = false, length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "key_type", nullable = false, length = 32)
	private ApiKeyType keyType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ApiKeyMode mode;

	@Column(name = "key_prefix", nullable = false, length = 40)
	private String keyPrefix;

	@Column(name = "secret_hash", length = 64)
	private String secretHash;

	@Column(name = "public_value", length = 128)
	private String publicValue;

	@Column(name = "created_by_user_id", nullable = false)
	private UUID createdByUserId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected ApiKeyEntity() {
	}

	public static ApiKeyEntity secret(UUID id, UUID merchantId, String name, ApiKeyMode mode, String keyPrefix,
			String secretHash, UUID createdByUserId, Instant createdAt) {
		ApiKeyEntity entity = new ApiKeyEntity();
		entity.id = id;
		entity.merchantId = merchantId;
		entity.name = name;
		entity.keyType = ApiKeyType.SECRET;
		entity.mode = mode;
		entity.keyPrefix = keyPrefix;
		entity.secretHash = secretHash;
		entity.createdByUserId = createdByUserId;
		entity.createdAt = createdAt;
		return entity;
	}

	public static ApiKeyEntity publishable(UUID id, UUID merchantId, String name, ApiKeyMode mode, String keyPrefix,
			String publicValue, UUID createdByUserId, Instant createdAt) {
		ApiKeyEntity entity = new ApiKeyEntity();
		entity.id = id;
		entity.merchantId = merchantId;
		entity.name = name;
		entity.keyType = ApiKeyType.PUBLISHABLE;
		entity.mode = mode;
		entity.keyPrefix = keyPrefix;
		entity.publicValue = publicValue;
		entity.createdByUserId = createdByUserId;
		entity.createdAt = createdAt;
		return entity;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public String getName() {
		return name;
	}

	public ApiKeyType getKeyType() {
		return keyType;
	}

	public ApiKeyMode getMode() {
		return mode;
	}

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public String getSecretHash() {
		return secretHash;
	}

	public String getPublicValue() {
		return publicValue;
	}

	public UUID getCreatedByUserId() {
		return createdByUserId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public void markUsed(Instant at) {
		this.lastUsedAt = at;
	}

	public void revoke(Instant at) {
		this.revokedAt = at;
	}
}
