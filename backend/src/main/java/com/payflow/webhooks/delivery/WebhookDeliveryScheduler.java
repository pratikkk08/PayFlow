package com.payflow.webhooks.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payflow.webhooks.delivery.enabled", havingValue = "true")
@ConditionalOnProperty(name = "payflow.webhooks.delivery.scheduling-enabled", havingValue = "true",
		matchIfMissing = true)
public class WebhookDeliveryScheduler {

	private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryScheduler.class);

	private final WebhookDeliveryWorker worker;

	public WebhookDeliveryScheduler(WebhookDeliveryWorker worker) {
		this.worker = worker;
	}

	@Scheduled(fixedDelayString = "${payflow.webhooks.delivery.poll-interval-ms:2000}")
	public void poll() {
		try {
			worker.deliverOnce();
		}
		catch (RuntimeException ex) {
			log.warn("Webhook delivery poll failed: {}", ex.toString());
		}
	}
}
