package com.payflow.webhooks.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.webhooks.delivery")
public class WebhookDeliveryProperties {

	/**
	 * When false (default), no outbound webhook HTTP is performed.
	 */
	private boolean enabled = false;

	private boolean schedulingEnabled = true;

	private int batchSize = 20;

	private long pollIntervalMs = 2000L;

	private long leaseSeconds = 30L;

	private int maxAttempts = 8;

	private long connectTimeoutMs = 3_000L;

	private long readTimeoutMs = 5_000L;

	private long overallTimeoutMs = 10_000L;

	private int maxResponseBytes = 8_192;

	private long backoffInitialMs = 2_000L;

	private long backoffMaxMs = 300_000L;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isSchedulingEnabled() {
		return schedulingEnabled;
	}

	public void setSchedulingEnabled(boolean schedulingEnabled) {
		this.schedulingEnabled = schedulingEnabled;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public long getPollIntervalMs() {
		return pollIntervalMs;
	}

	public void setPollIntervalMs(long pollIntervalMs) {
		this.pollIntervalMs = pollIntervalMs;
	}

	public long getLeaseSeconds() {
		return leaseSeconds;
	}

	public void setLeaseSeconds(long leaseSeconds) {
		this.leaseSeconds = leaseSeconds;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public long getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public void setConnectTimeoutMs(long connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
	}

	public long getReadTimeoutMs() {
		return readTimeoutMs;
	}

	public void setReadTimeoutMs(long readTimeoutMs) {
		this.readTimeoutMs = readTimeoutMs;
	}

	public long getOverallTimeoutMs() {
		return overallTimeoutMs;
	}

	public void setOverallTimeoutMs(long overallTimeoutMs) {
		this.overallTimeoutMs = overallTimeoutMs;
	}

	public int getMaxResponseBytes() {
		return maxResponseBytes;
	}

	public void setMaxResponseBytes(int maxResponseBytes) {
		this.maxResponseBytes = maxResponseBytes;
	}

	public long getBackoffInitialMs() {
		return backoffInitialMs;
	}

	public void setBackoffInitialMs(long backoffInitialMs) {
		this.backoffInitialMs = backoffInitialMs;
	}

	public long getBackoffMaxMs() {
		return backoffMaxMs;
	}

	public void setBackoffMaxMs(long backoffMaxMs) {
		this.backoffMaxMs = backoffMaxMs;
	}
}
