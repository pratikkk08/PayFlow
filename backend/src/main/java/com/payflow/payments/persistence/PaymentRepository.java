package com.payflow.payments.persistence;

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
}
