package com.payflow.outbox.publish;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.outbox.publisher")
public class OutboxPublisherProperties {

	/**
	 * When false (default), no scheduler or Kafka publish loop runs.
	 */
	private boolean enabled = false;

	/**
	 * When false, beans may exist for tests but {@code @Scheduled} polling is off.
	 */
	private boolean schedulingEnabled = true;

	private String topic = "payflow.domain-events.v1";

	private int batchSize = 20;

	private long pollIntervalMs = 2000L;

	private long leaseSeconds = 30L;

	private int maxAttempts = 8;

	private long sendTimeoutMs = 10_000L;

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

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
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

	public long getSendTimeoutMs() {
		return sendTimeoutMs;
	}

	public void setSendTimeoutMs(long sendTimeoutMs) {
		this.sendTimeoutMs = sendTimeoutMs;
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
