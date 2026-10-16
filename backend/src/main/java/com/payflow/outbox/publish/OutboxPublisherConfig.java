package com.payflow.outbox.publish;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(OutboxPublisherProperties.class)
public class OutboxPublisherConfig {

	@Configuration
	@ConditionalOnProperty(name = "payflow.outbox.publisher.enabled", havingValue = "true")
	@ConditionalOnProperty(name = "payflow.outbox.publisher.scheduling-enabled", havingValue = "true", matchIfMissing = true)
	@EnableScheduling
	static class SchedulingConfig {
	}
}
