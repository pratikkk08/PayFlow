package com.payflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka remains on the classpath for later phases. Phase 1 keeps brokers optional
 * unless {@code payflow.kafka.enabled=true}.
 */
@Configuration
public class KafkaPresenceConfig {

	private static final Logger log = LoggerFactory.getLogger(KafkaPresenceConfig.class);

	@Bean
	@ConditionalOnProperty(name = "payflow.kafka.enabled", havingValue = "false", matchIfMissing = true)
	ApplicationRunner kafkaDisabledNotice() {
		return args -> log.info("Kafka auto-configuration is disabled (payflow.kafka.enabled=false). "
				+ "Start Compose profile 'kafka' and set PAYFLOW_KAFKA_ENABLED=true to enable.");
	}

}
