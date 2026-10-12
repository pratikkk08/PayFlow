package com.payflow.merchants.api.dto;

import java.util.UUID;

import com.payflow.merchants.domain.ApiKeyMode;
import com.payflow.merchants.domain.ApiKeyType;
import com.payflow.merchants.domain.MerchantStatus;

public record IntegrationMeResponse(
		UUID merchantId,
		String merchantName,
		MerchantStatus merchantStatus,
		UUID apiKeyId,
		String keyPrefix,
		ApiKeyType keyType,
		ApiKeyMode mode) {
}
