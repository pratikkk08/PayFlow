package com.payflow.webhooks.delivery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.payflow.outbox.publish.DomainEventEnvelope;
import com.payflow.webhooks.domain.WebhookDeliveryEntity;
import com.payflow.webhooks.domain.WebhookEndpointEntity;
import com.payflow.webhooks.persistence.WebhookDeliveryRepository;
import com.payflow.webhooks.persistence.WebhookEndpointRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Creates PENDING webhook delivery jobs for matching enabled endpoints.
 * Must run inside the consumer's PostgreSQL transaction — never performs HTTP.
 */
@Component
public class WebhookDeliveryEnqueuer {

	private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryEnqueuer.class);

	private final WebhookEndpointRepository endpointRepository;
	private final WebhookDeliveryRepository deliveryRepository;
	private final JsonMapper jsonMapper;
	private final ObjectProvider<WebhookDeliveryEnqueueFaultInjector> faultInjector;

	public WebhookDeliveryEnqueuer(WebhookEndpointRepository endpointRepository,
			WebhookDeliveryRepository deliveryRepository,
			JsonMapper jsonMapper,
			ObjectProvider<WebhookDeliveryEnqueueFaultInjector> faultInjector) {
		this.endpointRepository = endpointRepository;
		this.deliveryRepository = deliveryRepository;
		this.jsonMapper = jsonMapper;
		this.faultInjector = faultInjector;
	}

	public int enqueueForEvent(DomainEventEnvelope envelope) {
		WebhookDeliveryEnqueueFaultInjector injector = faultInjector.getIfAvailable();
		if (injector != null) {
			injector.beforeEnqueue();
		}
		if (envelope.merchantId() == null) {
			return 0;
		}
		List<WebhookEndpointEntity> endpoints = endpointRepository
				.findByMerchantIdAndEnabledTrue(envelope.merchantId());
		if (endpoints.isEmpty()) {
			return 0;
		}

		String payloadJson;
		try {
			payloadJson = jsonMapper.writeValueAsString(envelope);
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("webhook_payload_serialize_failed", ex);
		}

		Instant now = Instant.now();
		int created = 0;
		for (WebhookEndpointEntity endpoint : endpoints) {
			if (!endpoint.getSubscribedEventTypes().contains(envelope.eventType())) {
				continue;
			}
			if (deliveryRepository.findByEventIdAndEndpointId(envelope.eventId(), endpoint.getId()).isPresent()) {
				continue;
			}
			WebhookDeliveryEntity job = new WebhookDeliveryEntity(
					UUID.randomUUID(),
					envelope.eventId(),
					endpoint.getId(),
					envelope.merchantId(),
					envelope.eventType(),
					payloadJson,
					now);
			try {
				deliveryRepository.saveAndFlush(job);
				created++;
			}
			catch (DataIntegrityViolationException ex) {
				log.debug("Duplicate webhook delivery skipped eventId={} endpointId={}",
						envelope.eventId(), endpoint.getId());
			}
		}
		if (created > 0) {
			log.info("Enqueued {} webhook delivery job(s) for eventId={} type={}",
					created, envelope.eventId(), envelope.eventType());
		}
		return created;
	}
}
