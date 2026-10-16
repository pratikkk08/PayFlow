package com.payflow.refunds.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class RefundIdempotencyRepositoryImpl implements RefundIdempotencyReservationRepository {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public boolean tryReserve(UUID id, UUID merchantId, String operation, String idempotencyKey, String fingerprint,
			Instant lockedAt, Instant expiresAt) {
		int inserted = entityManager.createNativeQuery("""
				INSERT INTO refund_idempotency_records (
				    id, merchant_id, operation, idempotency_key, request_fingerprint,
				    status, locked_at, expires_at, created_at, updated_at
				) VALUES (
				    ?1, ?2, ?3, ?4, ?5,
				    'STARTED', ?6, ?7, ?6, ?6
				)
				ON CONFLICT (merchant_id, operation, idempotency_key) DO NOTHING
				""")
				.setParameter(1, id)
				.setParameter(2, merchantId)
				.setParameter(3, operation)
				.setParameter(4, idempotencyKey)
				.setParameter(5, fingerprint)
				.setParameter(6, lockedAt)
				.setParameter(7, expiresAt)
				.executeUpdate();
		return inserted == 1;
	}
}
