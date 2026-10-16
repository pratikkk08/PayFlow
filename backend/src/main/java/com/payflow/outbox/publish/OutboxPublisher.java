package com.payflow.outbox.publish;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.payflow.operations.EventPipelineMetrics;
import com.payflow.operations.OutcomeLogs;
import com.payflow.outbox.domain.OutboxEventEntity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claims outbox rows in a short DB transaction, then publishes to Kafka outside that TX.
 * Delivery is at-least-once; consumers must dedupe by {@code eventId}.
 */
@Service
@ConditionalOnProperty(name = "payflow.outbox.publisher.enabled", havingValue = "true")
@ConditionalOnProperty(name = "payflow.kafka.enabled", havingValue = "true")
public class OutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

	private final OutboxClaimRepository claimRepository;
	private final OutboxPublisherProperties properties;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final JsonMapper jsonMapper;
	private final EventPipelineMetrics metrics;

	public OutboxPublisher(OutboxClaimRepository claimRepository,
			OutboxPublisherProperties properties,
			KafkaTemplate<String, String> kafkaTemplate,
			JsonMapper jsonMapper,
			EventPipelineMetrics metrics) {
		this.claimRepository = claimRepository;
		this.properties = properties;
		this.kafkaTemplate = kafkaTemplate;
		this.jsonMapper = jsonMapper;
		this.metrics = metrics;
	}

	/**
	 * One poll cycle: claim → Kafka send+ACK → conditional status update.
	 * @return number of events claimed in this cycle
	 */
	public int publishOnce() {
		Instant now = Instant.now();
		List<OutboxEventEntity> claimed = claimRepository.claimBatch(
				properties.getBatchSize(),
				Duration.ofSeconds(properties.getLeaseSeconds()),
				now,
				properties.getMaxAttempts());
		if (claimed.isEmpty()) {
			return 0;
		}

		for (OutboxEventEntity event : claimed) {
			processClaimed(event);
		}
		return claimed.size();
	}

	private void processClaimed(OutboxEventEntity event) {
		long started = System.nanoTime();
		if (event.getAttemptCount() > properties.getMaxAttempts()) {
			boolean marked = claimRepository.markFailed(event.getId(), event.getClaimToken(),
					"max_attempts_exhausted");
			if (marked) {
				metrics.recordOutboxPublish(elapsed(started), false);
			}
			log.warn("outbox_publish outcome=failed eventId={} eventType={} attempt={} reason=max_attempts_exhausted marked={}",
					event.getId(), event.getEventType(), event.getAttemptCount(), marked);
			return;
		}

		try {
			awaitKafkaAck(event);
			boolean published = claimRepository.markPublished(
					event.getId(), event.getClaimToken(), Instant.now());
			if (!published) {
				// ACK succeeded but claim lost — safe to republish later (at-least-once).
				log.warn("outbox_publish outcome=claim_lost eventId={} eventType={}",
						event.getId(), event.getEventType());
			}
			else {
				metrics.recordOutboxPublish(elapsed(started), true);
				log.info("outbox_publish outcome=published eventId={} eventType={} latencyMs={}",
						event.getId(), event.getEventType(), elapsed(started).toMillis());
			}
		}
		catch (Exception ex) {
			handlePublishFailure(event, ex, started);
		}
	}

	private void awaitKafkaAck(OutboxEventEntity event) throws Exception {
		DomainEventEnvelope envelope = toEnvelope(event);
		String value = jsonMapper.writeValueAsString(envelope);
		String key = event.getAggregateId().toString();

		ProducerRecord<String, String> record = new ProducerRecord<>(properties.getTopic(), key, value);
		record.headers().add(new RecordHeader("eventId", event.getId().toString().getBytes()));
		record.headers().add(new RecordHeader("eventType", event.getEventType().getBytes()));

		kafkaTemplate.send(record).get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
	}

	private void handlePublishFailure(OutboxEventEntity event, Exception ex, long started) {
		String message = OutcomeLogs.reason(sanitize(ex));
		boolean terminal = event.getAttemptCount() >= properties.getMaxAttempts();
		boolean marked;
		if (terminal) {
			marked = claimRepository.markFailed(event.getId(), event.getClaimToken(), message);
		}
		else {
			Instant next = Instant.now().plusMillis(backoffMillis(event.getAttemptCount()));
			marked = claimRepository.markRetry(event.getId(), event.getClaimToken(), next, message);
		}
		if (marked) {
			metrics.recordOutboxPublish(elapsed(started), false);
		}
		log.warn("outbox_publish outcome={} eventId={} eventType={} attempt={} reason={}",
				terminal ? "failed" : "retry", event.getId(), event.getEventType(),
				event.getAttemptCount(), message);
	}

	private static Duration elapsed(long startedNanos) {
		return Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos));
	}

	private long backoffMillis(int attemptCount) {
		long exp = properties.getBackoffInitialMs()
				* (1L << Math.min(Math.max(attemptCount - 1, 0), 16));
		return Math.min(properties.getBackoffMaxMs(), exp);
	}

	private DomainEventEnvelope toEnvelope(OutboxEventEntity event) {
		JsonNode payloadNode;
		try {
			payloadNode = jsonMapper.readTree(event.getPayload());
		}
		catch (RuntimeException ex) {
			payloadNode = jsonMapper.readTree("{}");
		}
		return new DomainEventEnvelope(
				event.getId(),
				event.getEventType(),
				event.getEventSchemaVersion(),
				event.getAggregateType(),
				event.getAggregateId(),
				event.getMerchantId(),
				event.getCreatedAt(),
				payloadNode);
	}

	private static String sanitize(Exception ex) {
		String raw = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "" : ex.getMessage());
		return raw.replaceAll("[\\r\\n\\t]+", " ").trim();
	}
}
