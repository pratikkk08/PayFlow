/**
 * Transactional outbox foundation for reliable domain-event publication.
 *
 * <p><strong>Write path:</strong> payment/refund services insert
 * {@code outbox_events} rows in the <em>same</em> PostgreSQL transaction as the
 * related state change (via {@link com.payflow.outbox.service.OutboxEventWriter}).
 * PostgreSQL remains the authoritative outbox store.
 *
 * <p><strong>Delivery:</strong> at-least-once via a later publisher (not yet wired).
 * Consumers must deduplicate by stable event id ({@code outbox_events.id}).
 *
 * <p><strong>Stable event identifiers (v1 catalog):</strong>
 * <ul>
 *   <li>Aggregates: {@code PAYMENT}, {@code REFUND}</li>
 *   <li>Emitted on success: {@code payment.created}, {@code payment.authorized},
 *       {@code payment.failed}, {@code payment.captured}, {@code payment.cancelled},
 *       {@code refund.succeeded}</li>
 *   <li>Catalog reserved (not emitted yet): {@code refund.failed}</li>
 * </ul>
 * Event schema versions start at {@code 1} per {@code event_type}.
 *
 * <p><strong>Future Kafka partition-key strategy:</strong> use
 * {@code aggregate_id} as the Kafka record key so events for one payment/refund
 * stay ordered on a partition. Put {@code event_id} (outbox PK) and
 * {@code event_type} in headers for consumer idempotency. Topic naming will follow
 * aggregate family (e.g. {@code payflow.payments}, {@code payflow.refunds}).
 * Prefer merchant-scoped consumer filters from payload/{@code merchant_id}, not
 * as the partition key, so aggregate ordering is preserved.
 *
 * <p><strong>Retry / FAILED inspection:</strong> publishers bump
 * {@code attempt_count}, apply backoff on {@code next_attempt_at}, and store a
 * sanitized {@code last_error} (≤512 chars). After a bounded attempt budget
 * (application policy), rows become {@code FAILED}. Ops can list FAILED via the
 * partial index and requeue by setting status back to {@code PENDING} with
 * {@code next_attempt_at = now()} (no public mutation API).
 *
 * <p>Payloads must exclude passwords, tokens, API secrets, and unnecessary PII.
 * <p>Kafka publisher ({@code OutboxPublisher}) claims with {@code FOR UPDATE SKIP LOCKED},
 * commits, then awaits broker ACK before conditional {@code PUBLISHED} updates keyed by
 * {@code claim_token}. Disabled unless {@code payflow.outbox.publisher.enabled=true}.
 *
 * <p>Idempotent consumer ({@code DomainEventKafkaListener}) dedupes via
 * {@code processed_events} (event id + consumer identity) in the same TX as handling.
 * Malformed/unsupported events and exhausted retries go to
 * {@code payflow.domain-events.v1.dlt}. Disabled unless
 * {@code payflow.kafka.consumer.enabled=true}. Initial handler records validated events only
 * — no financial side effects. No merchant webhooks yet.
 */
package com.payflow.outbox;
