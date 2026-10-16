package com.payflow.webhooks.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookEndpointResponse(
		UUID id,
		UUID merchantId,
		String url,
		String secretPrefix,
		boolean enabled,
		List<String> eventTypes,
		Instant createdAt,
		Instant updatedAt,
		Instant secretRotatedAt,
		long version) {
}
