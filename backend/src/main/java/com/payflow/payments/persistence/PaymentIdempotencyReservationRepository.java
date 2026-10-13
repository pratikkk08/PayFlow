package com.payflow.payments.persistence;

import java.time.Instant;
import java.util.UUID;

interface PaymentIdempotencyReservationRepository {

	/**
	 * Atomically reserves an idempotency key. Returns {@code true} when this caller
	 * owns the key; {@code false} when another record already exists for the scope.
	 * Concurrent inserts wait on the unique index until the peer commits or rolls back.
	 */
	boolean tryReserve(UUID id, UUID merchantId, String operation, String idempotencyKey, String fingerprint,
			Instant lockedAt, Instant expiresAt);
}
