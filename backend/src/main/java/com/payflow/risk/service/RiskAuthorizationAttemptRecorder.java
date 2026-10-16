package com.payflow.risk.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.risk.domain.RiskAuthorizationAttemptEntity;
import com.payflow.risk.persistence.RiskAuthorizationAttemptRepository;

/**
 * Idempotent per-payment authorize attempt insert. Unique {@code payment_id} plus
 * exists-check prevents double-counting retries of the same authorization request.
 */
@Service
public class RiskAuthorizationAttemptRecorder {

	private final RiskAuthorizationAttemptRepository attemptRepository;

	public RiskAuthorizationAttemptRecorder(RiskAuthorizationAttemptRepository attemptRepository) {
		this.attemptRepository = attemptRepository;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void recordAttempt(UUID merchantId, UUID paymentId, Instant attemptedAt) {
		if (attemptRepository.existsByPaymentId(paymentId)) {
			return;
		}
		attemptRepository.saveAndFlush(
				new RiskAuthorizationAttemptEntity(UUID.randomUUID(), merchantId, paymentId, attemptedAt));
	}
}
