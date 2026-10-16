/**
 * Merchant webhook endpoint registration and signed HTTPS delivery.
 *
 * <p><strong>Registration:</strong> JWT merchant OWNER CRUD for HTTPS endpoints. Signing secrets
 * returned plaintext once; AES-GCM at rest.
 *
 * <p><strong>Enqueue:</strong> Idempotent Kafka consumer inserts {@code webhook_deliveries} rows
 * in the same TX as {@code processed_events}. No HTTP in that transaction.
 *
 * <p><strong>Delivery ({@code payflow.webhooks.delivery.enabled}):</strong> Scheduler claims with
 * {@code FOR UPDATE SKIP LOCKED}, commits, then POSTs signed bodies via IP-pinned HTTPS
 * (SNI + cert hostname = original host). Retries 429/5xx/timeouts with exponential backoff + jitter;
 * other 4xx and SSRF/redirect failures are permanent {@code FAILED}. Exhausted jobs remain for inspection.
 *
 * <p><strong>Signature:</strong> {@code HMAC-SHA256} over
 * {@code {unixSeconds}.{eventId}.{body}} → header {@code X-PayFlow-Signature: v1=<hex>}.
 * See {@link com.payflow.webhooks.delivery.WebhookSignatureService}.
 *
 * <p><strong>SSRF:</strong> Re-resolve DNS each attempt; block private/metadata addresses; connect
 * only to a validated IP; reject redirects; never disable TLS verification. Registration validation
 * alone is not sufficient.
 */
package com.payflow.webhooks;
