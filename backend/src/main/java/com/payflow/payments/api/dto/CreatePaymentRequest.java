package com.payflow.payments.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
		@NotNull UUID merchantId,
		@NotBlank @Size(min = 1, max = 64) String merchantReference,
		@NotNull @Positive Long amountMinor,
		@NotBlank @Pattern(regexp = "INR", message = "currency must be INR") String currency,
		@Size(max = 500) String description) {
}
