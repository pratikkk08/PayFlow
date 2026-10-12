package com.payflow.merchants.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMerchantRequest(
		@NotBlank @Size(min = 2, max = 200) String name,
		@Size(max = 2000) String description) {
}
