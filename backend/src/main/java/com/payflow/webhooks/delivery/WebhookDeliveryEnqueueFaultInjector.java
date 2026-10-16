package com.payflow.webhooks.delivery;

/**
 * Optional test hook invoked inside the consumer TX immediately before delivery enqueue.
 */
public interface WebhookDeliveryEnqueueFaultInjector {

	void beforeEnqueue();
}
