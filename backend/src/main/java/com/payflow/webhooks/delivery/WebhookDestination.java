package com.payflow.webhooks.delivery;

import java.net.InetAddress;

/**
 * HTTPS destination with a single SSRF-validated IP selected for this attempt.
 * The TCP connection must target {@link #address()} while TLS SNI/hostname verification
 * uses {@link #hostname()}.
 */
public record WebhookDestination(
		String hostname,
		int port,
		String pathAndQuery,
		InetAddress address) {
}
