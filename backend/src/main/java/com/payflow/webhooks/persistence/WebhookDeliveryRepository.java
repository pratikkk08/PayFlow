package com.payflow.webhooks.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.webhooks.domain.WebhookDeliveryEntity;
import com.payflow.webhooks.domain.WebhookDeliveryStatus;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, UUID> {

	Optional<WebhookDeliveryEntity> findByEventIdAndEndpointId(UUID eventId, UUID endpointId);

	long countByEventId(UUID eventId);

	List<WebhookDeliveryEntity> findByStatusOrderByCreatedAtDescIdDesc(WebhookDeliveryStatus status);
}
