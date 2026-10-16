package com.payflow.webhooks.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.webhooks.domain.WebhookEndpointEntity;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpointEntity, UUID> {

	List<WebhookEndpointEntity> findByMerchantIdOrderByCreatedAtDescIdDesc(UUID merchantId);

	Optional<WebhookEndpointEntity> findByIdAndMerchantId(UUID id, UUID merchantId);

	boolean existsByMerchantIdAndUrl(UUID merchantId, String url);

	List<WebhookEndpointEntity> findByMerchantIdAndEnabledTrue(UUID merchantId);
}
