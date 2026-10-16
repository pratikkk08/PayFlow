package com.payflow.ledger.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.ledger.domain.LedgerEntryDirection;
import com.payflow.ledger.domain.LedgerEntryEntity;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {

	List<LedgerEntryEntity> findByLedgerTransactionId(UUID ledgerTransactionId);

	@Query("""
			select coalesce(sum(e.amountMinor), 0) from LedgerEntryEntity e
			where e.ledgerTransactionId = :transactionId and e.direction = :direction
			""")
	long sumAmountMinorByTransactionIdAndDirection(@Param("transactionId") UUID transactionId,
			@Param("direction") LedgerEntryDirection direction);
}
