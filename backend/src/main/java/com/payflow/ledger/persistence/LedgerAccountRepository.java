package com.payflow.ledger.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.ledger.domain.LedgerAccountEntity;
import com.payflow.ledger.domain.LedgerAccountType;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccountEntity, UUID> {

	Optional<LedgerAccountEntity> findByMerchantIdIsNullAndAccountTypeAndCurrency(
			LedgerAccountType accountType, String currency);

	Optional<LedgerAccountEntity> findByMerchantIdAndAccountTypeAndCurrency(
			UUID merchantId, LedgerAccountType accountType, String currency);
}
