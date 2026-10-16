package com.payflow.refunds.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.refunds.domain.RefundIdempotencyRecordEntity;

import jakarta.persistence.LockModeType;

public interface RefundIdempotencyRepository extends JpaRepository<RefundIdempotencyRecordEntity, UUID>,
		RefundIdempotencyReservationRepository {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select r from RefundIdempotencyRecordEntity r
			where r.merchantId = :merchantId
			  and r.operation = :operation
			  and r.idempotencyKey = :idempotencyKey
			""")
	Optional<RefundIdempotencyRecordEntity> findForUpdate(
			@Param("merchantId") UUID merchantId,
			@Param("operation") String operation,
			@Param("idempotencyKey") String idempotencyKey);
}
