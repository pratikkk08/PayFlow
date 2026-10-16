package com.payflow.webhooks.delivery;

/**
 * Outbound HTTPS transport for webhook delivery.
 * Implementations must pin to a validated destination IP and preserve TLS hostname checks.
 */
@FunctionalInterface
public interface WebhookHttpTransport {

	WebhookHttpResponse send(WebhookHttpRequest request, WebhookDestination destination);
}
