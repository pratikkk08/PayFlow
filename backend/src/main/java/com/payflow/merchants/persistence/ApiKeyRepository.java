package com.payflow.merchants.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.merchants.domain.ApiKeyEntity;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

	List<ApiKeyEntity> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

	Optional<ApiKeyEntity> findByIdAndMerchantId(UUID id, UUID merchantId);

	Optional<ApiKeyEntity> findBySecretHashAndRevokedAtIsNull(String secretHash);
}
