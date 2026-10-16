package com.payflow.outbox.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payflow.outbox.publisher.enabled", havingValue = "true")
@ConditionalOnProperty(name = "payflow.kafka.enabled", havingValue = "true")
@ConditionalOnProperty(name = "payflow.outbox.publisher.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherScheduler {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

	private final OutboxPublisher outboxPublisher;

	public OutboxPublisherScheduler(OutboxPublisher outboxPublisher) {
		this.outboxPublisher = outboxPublisher;
	}

	@Scheduled(fixedDelayString = "${payflow.outbox.publisher.poll-interval-ms:2000}")
	public void poll() {
		try {
			outboxPublisher.publishOnce();
		}
		catch (RuntimeException ex) {
			log.warn("Outbox publisher poll failed: {}", ex.toString());
		}
	}
}
