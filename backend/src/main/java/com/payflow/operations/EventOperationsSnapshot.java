package com.payflow.operations;

/**
 * Aggregate operational counts. No ids, payloads, or URLs.
 */
public record EventOperationsSnapshot(
		long outboxPending,
		long outboxProcessing,
		long outboxFailed,
		long outboxExpiredLeases,
		long webhookPending,
		long webhookProcessing,
		long webhookFailed,
		long webhookExpiredLeases) {
}
