package com.payflow.outbox.consume;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.outbox.publish.DomainEventEnvelope;
import com.payflow.webhooks.delivery.WebhookDeliveryEnqueuer;

/**
 * Idempotent domain-event handler.
 *
 * <p>In one PostgreSQL transaction: insert {@code processed_events} dedupe marker and enqueue
 * matching webhook delivery jobs. Never performs HTTP. Duplicates for the same
 * {@code (eventId, consumerIdentity)} are no-ops (no duplicate jobs).
 */
@Service
public class DomainEventProcessingService {

	private static final Logger log = LoggerFactory.getLogger(DomainEventProcessingService.class);

	private final ProcessedEventRepository processedEventRepository;
	private final DomainEventConsumerProperties properties;
	private final WebhookDeliveryEnqueuer webhookDeliveryEnqueuer;
	private final ObjectProvider<DomainEventProcessFaultInjector> faultInjector;

	public DomainEventProcessingService(ProcessedEventRepository processedEventRepository,
			DomainEventConsumerProperties properties,
			WebhookDeliveryEnqueuer webhookDeliveryEnqueuer,
			ObjectProvider<DomainEventProcessFaultInjector> faultInjector) {
		this.processedEventRepository = processedEventRepository;
		this.properties = properties;
		this.webhookDeliveryEnqueuer = webhookDeliveryEnqueuer;
		this.faultInjector = faultInjector;
	}

	/**
	 * @return {@code true} if this call inserted a new processed marker;
	 *         {@code false} if the event was already processed for this identity
	 */
	@Transactional
	public boolean processValidated(DomainEventEnvelope envelope) {
		return processInternal(envelope, properties.getIdentity());
	}

	/**
	 * Process using an explicit consumer identity (tests / multi-identity callers).
	 */
	@Transactional
	public boolean processValidatedForIdentity(DomainEventEnvelope envelope, String consumerIdentity) {
		return processInternal(envelope, consumerIdentity);
	}

	private boolean processInternal(DomainEventEnvelope envelope, String identity) {
		maybeInjectFault();

		if (processedEventRepository.existsByEventIdAndConsumerIdentity(envelope.eventId(), identity)) {
			log.debug("Skipping duplicate domain event eventId={} identity={}",
					envelope.eventId(), identity);
			return false;
		}

		ProcessedEventEntity row = new ProcessedEventEntity(
				envelope.eventId(),
				identity,
				envelope.eventType(),
				envelope.schemaVersion(),
				envelope.aggregateType(),
				envelope.aggregateId(),
				Instant.now());
		try {
			processedEventRepository.saveAndFlush(row);
		}
		catch (DataIntegrityViolationException ex) {
			log.debug("Concurrent duplicate domain event eventId={} identity={}",
					envelope.eventId(), identity);
			return false;
		}

		webhookDeliveryEnqueuer.enqueueForEvent(envelope);

		log.info("Recorded domain event eventId={} type={} identity={}",
				envelope.eventId(), envelope.eventType(), identity);
		return true;
	}

	private void maybeInjectFault() {
		DomainEventProcessFaultInjector injector = faultInjector.getIfAvailable();
		if (injector != null) {
			injector.beforeProcess();
		}
	}
}
