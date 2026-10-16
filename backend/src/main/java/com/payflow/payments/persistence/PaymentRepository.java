package com.payflow.payments.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.payments.domain.PaymentEntity;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID>, JpaSpecificationExecutor<PaymentEntity> {

	Optional<PaymentEntity> findByMerchantIdAndMerchantReference(UUID merchantId, String merchantReference);

	boolean existsByMerchantIdAndMerchantReference(UUID merchantId, String merchantReference);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PaymentEntity p where p.id = :id")
	Optional<PaymentEntity> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * Velocity window: merchant-scoped payments with {@code createdAt} in
	 * {@code [windowStart, windowEnd]} inclusive, excluding {@code excludePaymentId}.
	 */
	@Query("""
			select count(p) from PaymentEntity p
			where p.merchantId = :merchantId
			  and p.id <> :excludePaymentId
			  and p.createdAt >= :windowStart
			  and p.createdAt <= :windowEnd
			""")
	long countCreatedInWindowExcluding(
			@Param("merchantId") UUID merchantId,
			@Param("excludePaymentId") UUID excludePaymentId,
			@Param("windowStart") Instant windowStart,
			@Param("windowEnd") Instant windowEnd);

	/**
	 * Failed-authorization window: merchant-scoped {@code FAILED} payments with
	 * {@code updatedAt} in {@code [windowStart, windowEnd]} inclusive, excluding
	 * {@code excludePaymentId}. {@code updatedAt} reflects when the payment became FAILED.
	 */
	@Query("""
			select count(p) from PaymentEntity p
			where p.merchantId = :merchantId
			  and p.id <> :excludePaymentId
			  and p.status = com.payflow.payments.domain.PaymentStatus.FAILED
			  and p.updatedAt >= :windowStart
			  and p.updatedAt <= :windowEnd
			""")
	long countFailedUpdatedInWindowExcluding(
			@Param("merchantId") UUID merchantId,
			@Param("excludePaymentId") UUID excludePaymentId,
			@Param("windowStart") Instant windowStart,
			@Param("windowEnd") Instant windowEnd);
}
