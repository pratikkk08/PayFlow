package com.payflow.risk.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.risk.domain.RiskAuthorizationAttemptEntity;

public interface RiskAuthorizationAttemptRepository extends JpaRepository<RiskAuthorizationAttemptEntity, UUID> {

	boolean existsByPaymentId(UUID paymentId);

	/**
	 * Peer authorize attempts in {@code [windowStart, windowEnd]} inclusive, excluding
	 * {@code excludePaymentId}. Attempt rows are written at authorize-risk time
	 * (ALLOW/FLAG/REVIEW/BLOCK); retries of the same payment do not add another row.
	 */
	@Query("""
			select count(a) from RiskAuthorizationAttemptEntity a
			where a.merchantId = :merchantId
			  and a.paymentId <> :excludePaymentId
			  and a.attemptedAt >= :windowStart
			  and a.attemptedAt <= :windowEnd
			""")
	long countAttemptsInWindowExcluding(
			@Param("merchantId") UUID merchantId,
			@Param("excludePaymentId") UUID excludePaymentId,
			@Param("windowStart") Instant windowStart,
			@Param("windowEnd") Instant windowEnd);
}
