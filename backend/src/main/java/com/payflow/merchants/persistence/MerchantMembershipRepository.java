package com.payflow.merchants.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.merchants.domain.MerchantMembershipEntity;
import com.payflow.merchants.domain.MerchantMembershipRole;

public interface MerchantMembershipRepository extends JpaRepository<MerchantMembershipEntity, UUID> {

	Optional<MerchantMembershipEntity> findByMerchantIdAndUserId(UUID merchantId, UUID userId);

	boolean existsByMerchantIdAndUserIdAndRole(UUID merchantId, UUID userId, MerchantMembershipRole role);
}
