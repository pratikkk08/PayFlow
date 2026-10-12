package com.payflow.merchants.api.dto;

import com.payflow.merchants.domain.MerchantStatus;

import jakarta.validation.constraints.Size;

public record UpdateMerchantRequest(
		@Size(min = 2, max = 200) String name,
		@Size(max = 2000) String description,
		MerchantStatus status) {
}
