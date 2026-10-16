package com.payflow.webhooks.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateWebhookEndpointRequest(
		@NotBlank @Size(max = 2048) String url,
		@NotEmpty List<@NotBlank @Size(max = 128) String> eventTypes,
		Boolean enabled) {
}
