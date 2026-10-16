package com.payflow.webhooks;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.payflow.webhooks.delivery.WebhookDeliveryEnqueueFaultInjector;

@Component
@Profile("test")
class TestWebhookDeliveryEnqueueFaultInjector implements WebhookDeliveryEnqueueFaultInjector {

	private final AtomicBoolean failNext = new AtomicBoolean(false);

	void failNextEnqueue() {
		failNext.set(true);
	}

	void clear() {
		failNext.set(false);
	}

	@Override
	public void beforeEnqueue() {
		if (failNext.compareAndSet(true, false)) {
			throw new IllegalStateException("injected_webhook_enqueue_failure");
		}
	}
}
