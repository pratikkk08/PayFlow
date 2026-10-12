package com.payflow.merchants.api.dto;

import com.payflow.merchants.domain.ApiKeyType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(
		@NotBlank @Size(min = 2, max = 120) String name,
		ApiKeyType type) {
}
