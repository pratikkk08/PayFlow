package com.payflow.merchants.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.payflow.merchants.domain.MerchantStatus;

public record MerchantResponse(
		UUID id,
		String name,
		String description,
		MerchantStatus status,
		UUID ownerUserId,
		Instant createdAt,
		Instant updatedAt) {
}
