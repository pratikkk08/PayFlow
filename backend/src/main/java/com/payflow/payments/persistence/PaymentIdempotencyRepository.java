package com.payflow.payments.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.payments.domain.PaymentIdempotencyRecordEntity;

import jakarta.persistence.LockModeType;

public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotencyRecordEntity, UUID>,
		PaymentIdempotencyReservationRepository {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select r from PaymentIdempotencyRecordEntity r
			where r.merchantId = :merchantId
			  and r.operation = :operation
			  and r.idempotencyKey = :idempotencyKey
			""")
	Optional<PaymentIdempotencyRecordEntity> findForUpdate(
			@Param("merchantId") UUID merchantId,
			@Param("operation") String operation,
			@Param("idempotencyKey") String idempotencyKey);

	Optional<PaymentIdempotencyRecordEntity> findByMerchantIdAndOperationAndIdempotencyKey(
			UUID merchantId, String operation, String idempotencyKey);
}
