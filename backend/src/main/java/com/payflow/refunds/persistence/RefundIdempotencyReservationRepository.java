package com.payflow.refunds.persistence;

import java.time.Instant;
import java.util.UUID;

interface RefundIdempotencyReservationRepository {

	/**
	 * Atomically reserves an idempotency key. Returns {@code true} when this caller
	 * owns the key; {@code false} when another record already exists for the scope.
	 */
	boolean tryReserve(UUID id, UUID merchantId, String operation, String idempotencyKey, String fingerprint,
			Instant lockedAt, Instant expiresAt);
}
