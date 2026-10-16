package com.payflow.webhooks.delivery;

import java.util.Map;

public record WebhookHttpRequest(
		String url,
		String body,
		Map<String, String> headers) {
}
