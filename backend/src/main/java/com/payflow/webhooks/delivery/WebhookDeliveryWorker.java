package com.payflow.webhooks.delivery;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.payflow.operations.EventPipelineMetrics;
import com.payflow.operations.OutcomeLogs;
import com.payflow.webhooks.domain.WebhookDeliveryEntity;
import com.payflow.webhooks.domain.WebhookEndpointEntity;
import com.payflow.webhooks.persistence.WebhookEndpointRepository;
import com.payflow.webhooks.service.WebhookSecretCrypto;

/**
 * Claims due webhook deliveries, commits, then performs signed HTTPS outside the claim TX.
 */
@Service
@ConditionalOnProperty(name = "payflow.webhooks.delivery.enabled", havingValue = "true")
public class WebhookDeliveryWorker {

	private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryWorker.class);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final WebhookDeliveryClaimRepository claimRepository;
	private final WebhookEndpointRepository endpointRepository;
	private final WebhookSecretCrypto secretCrypto;
	private final WebhookSignatureService signatureService;
	private final WebhookDestinationResolver destinationResolver;
	private final WebhookHttpTransport httpTransport;
	private final WebhookDeliveryProperties properties;
	private final EventPipelineMetrics metrics;

	public WebhookDeliveryWorker(WebhookDeliveryClaimRepository claimRepository,
			WebhookEndpointRepository endpointRepository,
			WebhookSecretCrypto secretCrypto,
			WebhookSignatureService signatureService,
			WebhookDestinationResolver destinationResolver,
			WebhookHttpTransport httpTransport,
			WebhookDeliveryProperties properties,
			EventPipelineMetrics metrics) {
		this.claimRepository = claimRepository;
		this.endpointRepository = endpointRepository;
		this.secretCrypto = secretCrypto;
		this.signatureService = signatureService;
		this.destinationResolver = destinationResolver;
		this.httpTransport = httpTransport;
		this.properties = properties;
		this.metrics = metrics;
	}

	/**
	 * @return number of deliveries claimed in this cycle
	 */
	public int deliverOnce() {
		Instant now = Instant.now();
		List<WebhookDeliveryEntity> claimed = claimRepository.claimBatch(
				properties.getBatchSize(),
				Duration.ofSeconds(properties.getLeaseSeconds()),
				now,
				properties.getMaxAttempts());
		if (claimed.isEmpty()) {
			return 0;
		}
		for (WebhookDeliveryEntity delivery : claimed) {
			processClaimed(delivery);
		}
		return claimed.size();
	}

	private void processClaimed(WebhookDeliveryEntity delivery) {
		long started = System.nanoTime();
		if (delivery.getAttemptCount() > properties.getMaxAttempts()) {
			boolean marked = claimRepository.markFailed(delivery.getId(), delivery.getClaimToken(), null,
					"max_attempts_exhausted");
			recordTerminal(started, false, marked);
			log.warn("webhook_delivery outcome=failed deliveryId={} eventId={} reason=max_attempts_exhausted marked={}",
					delivery.getId(), delivery.getEventId(), marked);
			return;
		}

		Optional<WebhookEndpointEntity> endpointOpt = endpointRepository.findById(delivery.getEndpointId());
		if (endpointOpt.isEmpty() || !endpointOpt.get().isEnabled()) {
			finishFailed(delivery, started, null, "endpoint_disabled_or_deleted");
			return;
		}
		WebhookEndpointEntity endpoint = endpointOpt.get();
		if (!endpoint.getSubscribedEventTypes().contains(delivery.getEventType())) {
			finishFailed(delivery, started, null, "endpoint_not_subscribed");
			return;
		}

		try {
			WebhookDestination destination = destinationResolver.resolve(endpoint.getUrl());
			String secret = secretCrypto.decrypt(endpoint.getSigningSecretCiphertext());
			long timestamp = Instant.now().getEpochSecond();
			String eventId = delivery.getEventId().toString();
			String body = delivery.getPayload();
			String signature = signatureService.sign(secret, timestamp, eventId, body);

			Map<String, String> headers = new LinkedHashMap<>();
			headers.put(WebhookSignatureService.HEADER_EVENT_ID, eventId);
			headers.put(WebhookSignatureService.HEADER_TIMESTAMP, Long.toString(timestamp));
			headers.put(WebhookSignatureService.HEADER_SIGNATURE, signature);

			WebhookHttpResponse response = httpTransport.send(
					new WebhookHttpRequest(endpoint.getUrl(), body, headers), destination);
			handleHttpResult(delivery, response, started);
		}
		catch (WebhookDeliveryException ex) {
			handleDeliveryException(delivery, ex, started);
		}
		catch (RuntimeException ex) {
			handleDeliveryException(delivery, new WebhookDeliveryException(
					WebhookDeliveryFailureKind.RETRYABLE, sanitize(ex), null, ex), started);
		}
	}

	private void handleHttpResult(WebhookDeliveryEntity delivery, WebhookHttpResponse response, long started) {
		int status = response.statusCode();
		if (status >= 200 && status < 300) {
			boolean ok = claimRepository.markDelivered(
					delivery.getId(), delivery.getClaimToken(), Instant.now(), status);
			if (!ok) {
				log.warn("webhook_delivery outcome=claim_lost deliveryId={} eventId={} httpStatus={}",
						delivery.getId(), delivery.getEventId(), status);
			}
			else {
				recordTerminal(started, true, true);
				log.info("webhook_delivery outcome=delivered deliveryId={} eventId={} httpStatus={} latencyMs={}",
						delivery.getId(), delivery.getEventId(), status, elapsed(started).toMillis());
			}
			return;
		}
		if (status == 429 || status >= 500) {
			scheduleRetryOrFail(delivery, status, "http_" + status, started);
			return;
		}
		finishFailed(delivery, started, status, "http_" + status);
	}

	private void handleDeliveryException(WebhookDeliveryEntity delivery, WebhookDeliveryException ex, long started) {
		String reason = OutcomeLogs.reason(ex.getMessage());
		if (ex.getKind() == WebhookDeliveryFailureKind.PERMANENT) {
			finishFailed(delivery, started, ex.getHttpStatus(), reason);
			return;
		}
		scheduleRetryOrFail(delivery, ex.getHttpStatus(), reason, started);
	}

	private void scheduleRetryOrFail(WebhookDeliveryEntity delivery, Integer httpStatus, String error, long started) {
		String reason = OutcomeLogs.reason(error);
		if (delivery.getAttemptCount() >= properties.getMaxAttempts()) {
			boolean marked = claimRepository.markFailed(delivery.getId(), delivery.getClaimToken(), httpStatus,
					reason);
			recordTerminal(started, false, marked);
			log.warn("webhook_delivery outcome=failed deliveryId={} eventId={} httpStatus={} reason={}",
					delivery.getId(), delivery.getEventId(), httpStatus, reason);
			return;
		}
		boolean marked = claimRepository.markRetry(delivery.getId(), delivery.getClaimToken(),
				Instant.now().plusMillis(backoffMillis(delivery.getAttemptCount())), httpStatus, reason);
		if (marked) {
			metrics.recordWebhookRetry(elapsed(started));
		}
		log.warn("webhook_delivery outcome=retry deliveryId={} eventId={} httpStatus={} attempt={} reason={}",
				delivery.getId(), delivery.getEventId(), httpStatus, delivery.getAttemptCount(), reason);
	}

	private void finishFailed(WebhookDeliveryEntity delivery, long started, Integer httpStatus, String reason) {
		String safeReason = OutcomeLogs.reason(reason);
		boolean marked = claimRepository.markFailed(delivery.getId(), delivery.getClaimToken(), httpStatus, safeReason);
		recordTerminal(started, false, marked);
		log.warn("webhook_delivery outcome=failed deliveryId={} eventId={} httpStatus={} reason={}",
				delivery.getId(), delivery.getEventId(), httpStatus, safeReason);
	}

	private void recordTerminal(long started, boolean success, boolean marked) {
		if (marked) {
			metrics.recordWebhookDelivery(elapsed(started), success);
		}
	}

	private static Duration elapsed(long startedNanos) {
		return Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos));
	}

	private long backoffMillis(int attemptCount) {
		long exp = properties.getBackoffInitialMs()
				* (1L << Math.min(Math.max(attemptCount - 1, 0), 16));
		long capped = Math.min(properties.getBackoffMaxMs(), exp);
		long jitter = RANDOM.nextLong(Math.max(1L, capped / 5L + 1L));
		return Math.min(properties.getBackoffMaxMs(), capped - (capped / 10L) + jitter);
	}

	private static String sanitize(Throwable ex) {
		String raw = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "" : ex.getMessage());
		return raw.replaceAll("[\\r\\n\\t]+", " ").trim();
	}
}
