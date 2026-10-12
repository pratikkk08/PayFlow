package com.payflow.merchants.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.payflow.merchants.domain.ApiKeyMode;
import com.payflow.merchants.domain.ApiKeyType;

public record ApiKeyCreatedResponse(
		UUID id,
		UUID merchantId,
		String name,
		ApiKeyType type,
		ApiKeyMode mode,
		String keyPrefix,
		String secret,
		Instant createdAt) {
}
