package com.payflow.operations;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
@ConditionalOnProperty(name = "payflow.operations.metrics-refresh-enabled", havingValue = "true", matchIfMissing = true)
public class EventOperationsScheduler {

	private final ThreadPoolTaskScheduler scheduler;

	public EventOperationsScheduler(EventOperationsService operationsService, EventOperationsProperties properties) {
		this.scheduler = new ThreadPoolTaskScheduler();
		this.scheduler.setPoolSize(1);
		this.scheduler.setThreadNamePrefix("payflow-ops-metrics-");
		this.scheduler.initialize();
		this.scheduler.scheduleWithFixedDelay(operationsService::refresh,
				Duration.ofMillis(Math.max(1_000L, properties.getMetricsRefreshMs())));
	}

	@PreDestroy
	void shutdown() {
		scheduler.shutdown();
	}
}
