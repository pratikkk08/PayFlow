package com.payflow.refunds.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.refunds.domain.RefundEntity;
import com.payflow.refunds.domain.RefundStatus;

public interface RefundRepository extends JpaRepository<RefundEntity, UUID> {

	Optional<RefundEntity> findByMerchantIdAndMerchantReference(UUID merchantId, String merchantReference);

	boolean existsByMerchantIdAndMerchantReference(UUID merchantId, String merchantReference);

	Page<RefundEntity> findByPaymentIdAndMerchantId(UUID paymentId, UUID merchantId, Pageable pageable);

	@Query("""
			select coalesce(sum(r.amountMinor), 0) from RefundEntity r
			where r.paymentId = :paymentId and r.status in :statuses
			""")
	long sumAmountMinorByPaymentIdAndStatusIn(@Param("paymentId") UUID paymentId,
			@Param("statuses") Collection<RefundStatus> statuses);
}
