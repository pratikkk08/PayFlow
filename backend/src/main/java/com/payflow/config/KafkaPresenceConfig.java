package com.payflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka is optional: payment/refund APIs never require a broker. Outbox publishing
 * is separately gated by {@code payflow.outbox.publisher.enabled}.
 */
@Configuration
public class KafkaPresenceConfig {

	private static final Logger log = LoggerFactory.getLogger(KafkaPresenceConfig.class);

	@Bean
	@ConditionalOnProperty(name = "payflow.kafka.enabled", havingValue = "false", matchIfMissing = true)
	ApplicationRunner kafkaDisabledNotice() {
		return args -> log.info("Kafka remains optional (payflow.kafka.enabled=false). "
				+ "Start Compose profile 'kafka', set PAYFLOW_KAFKA_ENABLED=true, and enable "
				+ "payflow.outbox.publisher.enabled to relay outbox events.");
	}

	@Bean
	@ConditionalOnProperty(name = "payflow.outbox.publisher.enabled", havingValue = "true")
	ApplicationRunner outboxPublisherEnabledNotice() {
		return args -> log.info("Outbox Kafka publisher enabled (topic payflow.domain-events.v1); "
				+ "delivery is at-least-once.");
	}

	@Bean
	@ConditionalOnProperty(name = "payflow.kafka.consumer.enabled", havingValue = "false", matchIfMissing = true)
	ApplicationRunner kafkaConsumerDisabledNotice() {
		return args -> log.info("Domain-event Kafka consumer disabled "
				+ "(payflow.kafka.consumer.enabled=false). Set PAYFLOW_KAFKA_CONSUMER_ENABLED=true "
				+ "with Kafka to record validated events via processed_events.");
	}

}
