package com.payflow.outbox.consume;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.kafka.consumer")
public class DomainEventConsumerProperties {

	/**
	 * When false (default), no domain-event Kafka listener is registered.
	 * Also requires {@code payflow.kafka.enabled=true}.
	 */
	private boolean enabled = false;

	/**
	 * Logical consumer identity used in {@code processed_events} deduplication.
	 * Distinct identities may process the same event id independently.
	 */
	private String identity = "payflow-domain-events-recorder";

	private String groupId = "payflow-domain-events-internal";

	private String topic = "payflow.domain-events.v1";

	/**
	 * Dead-letter topic for malformed / unsupported / exhausted-retry records.
	 */
	private String dltTopic = "payflow.domain-events.v1.dlt";

	/**
	 * Total delivery attempts including the first (e.g. 3 ⇒ 1 try + 2 retries).
	 */
	private int maxAttempts = 3;

	private long backoffIntervalMs = 500L;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getIdentity() {
		return identity;
	}

	public void setIdentity(String identity) {
		this.identity = identity;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public String getDltTopic() {
		return dltTopic;
	}

	public void setDltTopic(String dltTopic) {
		this.dltTopic = dltTopic;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public long getBackoffIntervalMs() {
		return backoffIntervalMs;
	}

	public void setBackoffIntervalMs(long backoffIntervalMs) {
		this.backoffIntervalMs = backoffIntervalMs;
	}
}
