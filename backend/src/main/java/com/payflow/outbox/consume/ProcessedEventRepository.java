package com.payflow.outbox.consume;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventId> {

	boolean existsByEventIdAndConsumerIdentity(UUID eventId, String consumerIdentity);

	long countByEventId(UUID eventId);

	long countByEventIdAndConsumerIdentity(UUID eventId, String consumerIdentity);
}
