package com.payflow.merchants.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.merchants.domain.MerchantEntity;

import jakarta.persistence.LockModeType;

public interface MerchantRepository extends JpaRepository<MerchantEntity, UUID> {

	/**
	 * Merchant-scoped risk serialization for authorize (Task 6.4). Always acquire
	 * <b>after</b> the payment row lock to keep lock order: payment → merchant.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from MerchantEntity m where m.id = :id")
	Optional<MerchantEntity> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			select m from MerchantEntity m
			where m.id = :merchantId
			  and exists (
			    select 1 from MerchantMembershipEntity mm
			    where mm.merchantId = m.id and mm.userId = :userId
			  )
			""")
	Optional<MerchantEntity> findAccessible(@Param("merchantId") UUID merchantId, @Param("userId") UUID userId);

	@Query("""
			select m from MerchantEntity m
			where exists (
			  select 1 from MerchantMembershipEntity mm
			  where mm.merchantId = m.id and mm.userId = :userId
			)
			order by m.createdAt desc
			""")
	List<MerchantEntity> findAllAccessible(@Param("userId") UUID userId);
}
