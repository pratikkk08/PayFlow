package com.payflow.operations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Low-cardinality pipeline gauges, delivery counters, and latency timers.
 * Labels are status or outcome only — never event, merchant, or URL.
 */
@Component
public class EventPipelineMetrics {

	private final AtomicLong outboxPending = new AtomicLong();
	private final AtomicLong outboxProcessing = new AtomicLong();
	private final AtomicLong outboxFailed = new AtomicLong();
	private final AtomicLong outboxExpiredLeases = new AtomicLong();
	private final AtomicLong webhookPending = new AtomicLong();
	private final AtomicLong webhookProcessing = new AtomicLong();
	private final AtomicLong webhookFailed = new AtomicLong();
	private final AtomicLong webhookExpiredLeases = new AtomicLong();

	private final Counter webhookSuccess;
	private final Counter webhookFailure;
	private final Timer publishSuccessLatency;
	private final Timer publishFailureLatency;
	private final Timer deliverySuccessLatency;
	private final Timer deliveryFailureLatency;
	private final Timer deliveryRetryLatency;

	public EventPipelineMetrics(MeterRegistry registry) {
		gauge(registry, "payflow.outbox.events", "status", "pending", outboxPending);
		gauge(registry, "payflow.outbox.events", "status", "processing", outboxProcessing);
		gauge(registry, "payflow.outbox.events", "status", "failed", outboxFailed);
		gauge(registry, "payflow.outbox.expired_leases", "pipeline", "outbox", outboxExpiredLeases);
		gauge(registry, "payflow.webhook.deliveries.backlog", "status", "pending", webhookPending);
		gauge(registry, "payflow.webhook.deliveries.backlog", "status", "processing", webhookProcessing);
		gauge(registry, "payflow.webhook.deliveries.backlog", "status", "failed", webhookFailed);
		gauge(registry, "payflow.webhook.expired_leases", "pipeline", "webhook", webhookExpiredLeases);

		webhookSuccess = registry.counter("payflow.webhook.deliveries", "outcome", "success");
		webhookFailure = registry.counter("payflow.webhook.deliveries", "outcome", "failure");
		publishSuccessLatency = registry.timer("payflow.outbox.publish.latency", "outcome", "success");
		publishFailureLatency = registry.timer("payflow.outbox.publish.latency", "outcome", "failure");
		deliverySuccessLatency = registry.timer("payflow.webhook.delivery.latency", "outcome", "success");
		deliveryFailureLatency = registry.timer("payflow.webhook.delivery.latency", "outcome", "failure");
		deliveryRetryLatency = registry.timer("payflow.webhook.delivery.latency", "outcome", "retry");
	}

	public void apply(EventOperationsSnapshot snapshot) {
		outboxPending.set(snapshot.outboxPending());
		outboxProcessing.set(snapshot.outboxProcessing());
		outboxFailed.set(snapshot.outboxFailed());
		outboxExpiredLeases.set(snapshot.outboxExpiredLeases());
		webhookPending.set(snapshot.webhookPending());
		webhookProcessing.set(snapshot.webhookProcessing());
		webhookFailed.set(snapshot.webhookFailed());
		webhookExpiredLeases.set(snapshot.webhookExpiredLeases());
	}

	public void recordOutboxPublish(Duration latency, boolean success) {
		(success ? publishSuccessLatency : publishFailureLatency).record(latency);
	}

	public void recordWebhookDelivery(Duration latency, boolean success) {
		(success ? deliverySuccessLatency : deliveryFailureLatency).record(latency);
		(success ? webhookSuccess : webhookFailure).increment();
	}

	public void recordWebhookRetry(Duration latency) {
		deliveryRetryLatency.record(latency);
	}

	private static void gauge(MeterRegistry registry, String name, String tagKey, String tagValue, AtomicLong value) {
		Gauge.builder(name, value, AtomicLong::doubleValue)
				.tag(tagKey, tagValue)
				.register(registry);
	}
}
