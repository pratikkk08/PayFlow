package com.payflow.webhooks.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Create response — {@code signingSecret} is returned only once and must never appear in list/get.
 */
public record WebhookEndpointCreatedResponse(
		UUID id,
		UUID merchantId,
		String url,
		String secretPrefix,
		String signingSecret,
		boolean enabled,
		List<String> eventTypes,
		Instant createdAt,
		Instant updatedAt,
		Instant secretRotatedAt,
		long version) {
}
