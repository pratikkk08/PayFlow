package com.payflow.operations;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.operations")
public class EventOperationsProperties {

	private boolean metricsRefreshEnabled = true;

	private long metricsRefreshMs = 15_000L;

	public boolean isMetricsRefreshEnabled() {
		return metricsRefreshEnabled;
	}

	public void setMetricsRefreshEnabled(boolean metricsRefreshEnabled) {
		this.metricsRefreshEnabled = metricsRefreshEnabled;
	}

	public long getMetricsRefreshMs() {
		return metricsRefreshMs;
	}

	public void setMetricsRefreshMs(long metricsRefreshMs) {
		this.metricsRefreshMs = metricsRefreshMs;
	}
}
