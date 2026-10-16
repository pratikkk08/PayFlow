package com.payflow.operations.api;

import com.payflow.operations.EventOperationsSnapshot;

public record EventOperationsHealthResponse(Counts outbox, Counts webhooks) {

	public record Counts(long pending, long processing, long failed, long expiredLeases) {
	}

	public static EventOperationsHealthResponse from(EventOperationsSnapshot snapshot) {
		return new EventOperationsHealthResponse(
				new Counts(snapshot.outboxPending(), snapshot.outboxProcessing(), snapshot.outboxFailed(),
						snapshot.outboxExpiredLeases()),
				new Counts(snapshot.webhookPending(), snapshot.webhookProcessing(), snapshot.webhookFailed(),
						snapshot.webhookExpiredLeases()));
	}
}
