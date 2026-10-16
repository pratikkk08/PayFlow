package com.payflow.refunds.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRefundRequest(
		@NotBlank @Size(min = 1, max = 64) String merchantReference,
		@NotNull @Positive Long amountMinor,
		@Size(max = 500) String reason) {
}
