package com.payflow.outbox.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.outbox.domain.OutboxEventEntity;
import com.payflow.outbox.domain.OutboxEventStatus;

/**
 * Persistence for outbox rows. Polling helpers support a future concurrent publisher;
 * no scheduling or Kafka relay is enabled yet.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

	@Query("""
			select e from OutboxEventEntity e
			where e.status = com.payflow.outbox.domain.OutboxEventStatus.PENDING
			  and e.nextAttemptAt <= :now
			order by e.nextAttemptAt asc, e.id asc
			""")
	List<OutboxEventEntity> findPendingDue(@Param("now") Instant now, Pageable pageable);

	@Query("""
			select e from OutboxEventEntity e
			where e.status = com.payflow.outbox.domain.OutboxEventStatus.PROCESSING
			  and e.claimExpiresAt < :now
			order by e.claimExpiresAt asc, e.id asc
			""")
	List<OutboxEventEntity> findExpiredClaims(@Param("now") Instant now, Pageable pageable);

	List<OutboxEventEntity> findByStatusOrderByCreatedAtDescIdDesc(OutboxEventStatus status, Pageable pageable);

	List<OutboxEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
			String aggregateType, UUID aggregateId);
}
