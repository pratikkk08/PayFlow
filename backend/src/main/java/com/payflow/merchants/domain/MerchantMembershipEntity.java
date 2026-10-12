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
@Table(name = "merchant_memberships")
public class MerchantMembershipEntity {

	@Id
	private UUID id;

	@Column(name = "merchant_id", nullable = false)
	private UUID merchantId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MerchantMembershipRole role;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected MerchantMembershipEntity() {
	}

	public MerchantMembershipEntity(UUID id, UUID merchantId, UUID userId, MerchantMembershipRole role,
			Instant createdAt) {
		this.id = id;
		this.merchantId = merchantId;
		this.userId = userId;
		this.role = role;
		this.createdAt = createdAt;
	}

	public UUID getMerchantId() {
		return merchantId;
	}

	public UUID getUserId() {
		return userId;
	}

	public MerchantMembershipRole getRole() {
		return role;
	}
}
