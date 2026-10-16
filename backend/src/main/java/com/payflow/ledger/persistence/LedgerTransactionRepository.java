package com.payflow.ledger.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.ledger.domain.LedgerSourceType;
import com.payflow.ledger.domain.LedgerTransactionEntity;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, UUID> {

	Optional<LedgerTransactionEntity> findBySourceTypeAndSourceId(LedgerSourceType sourceType, UUID sourceId);

	boolean existsBySourceTypeAndSourceId(LedgerSourceType sourceType, UUID sourceId);
}
