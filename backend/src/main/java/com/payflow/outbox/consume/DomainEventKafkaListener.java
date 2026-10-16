package com.payflow.outbox.consume;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Internal consumer for {@code payflow.domain-events.v1}.
 *
 * <p><strong>Ack policy:</strong> manual immediate ack only after the processing
 * transaction commits successfully. Failures are never acknowledged as success —
 * {@link DomainEventConsumerConfig} retries then recovers to the DLT (and commits
 * the offset only after durable DLT publication).
 *
 * <p><strong>Error / DLT strategy:</strong>
 * <ul>
 *   <li>{@link InvalidDomainEventException} (malformed / unsupported) → no retry → DLT</li>
 *   <li>Transient handler/DB failures → bounded retries → DLT if exhausted</li>
 *   <li>Successful processing or duplicate dedupe → ack</li>
 * </ul>
 *
 * <p>Rebalances / redelivery are safe: {@code processed_events} PK prevents duplicate
 * side effects for the same consumer identity.
 */
@Component
@ConditionalOnProperty(name = "payflow.kafka.enabled", havingValue = "true")
@ConditionalOnProperty(name = "payflow.kafka.consumer.enabled", havingValue = "true")
public class DomainEventKafkaListener {

	private static final Logger log = LoggerFactory.getLogger(DomainEventKafkaListener.class);

	private final DomainEventValidator validator;
	private final DomainEventProcessingService processingService;

	public DomainEventKafkaListener(DomainEventValidator validator,
			DomainEventProcessingService processingService) {
		this.validator = validator;
		this.processingService = processingService;
	}

	@KafkaListener(
			topics = "${payflow.kafka.consumer.topic}",
			groupId = "${payflow.kafka.consumer.group-id}",
			containerFactory = "domainEventKafkaListenerContainerFactory")
	public void onDomainEvent(String payload, Acknowledgment acknowledgment) {
		var envelope = validator.parseAndValidate(payload);
		boolean inserted = processingService.processValidated(envelope);
		acknowledgment.acknowledge();
		log.debug("Acknowledged domain event eventId={} newlyRecorded={}",
				envelope.eventId(), inserted);
	}
}
