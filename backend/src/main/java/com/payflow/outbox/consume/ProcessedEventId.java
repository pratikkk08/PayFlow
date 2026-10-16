package com.payflow.outbox.consume;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ProcessedEventId implements Serializable {

	private UUID eventId;
	private String consumerIdentity;

	public ProcessedEventId() {
	}

	public ProcessedEventId(UUID eventId, String consumerIdentity) {
		this.eventId = eventId;
		this.consumerIdentity = consumerIdentity;
	}

	public UUID getEventId() {
		return eventId;
	}

	public void setEventId(UUID eventId) {
		this.eventId = eventId;
	}

	public String getConsumerIdentity() {
		return consumerIdentity;
	}

	public void setConsumerIdentity(String consumerIdentity) {
		this.consumerIdentity = consumerIdentity;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ProcessedEventId that)) {
			return false;
		}
		return Objects.equals(eventId, that.eventId)
				&& Objects.equals(consumerIdentity, that.consumerIdentity);
	}

	@Override
	public int hashCode() {
		return Objects.hash(eventId, consumerIdentity);
	}
}
