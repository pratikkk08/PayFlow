package com.payflow.ledger.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.ledger.domain.LedgerAccountEntity;
import com.payflow.ledger.domain.LedgerAccountType;
import com.payflow.ledger.domain.LedgerEntryDirection;
import com.payflow.ledger.domain.LedgerEntryEntity;
import com.payflow.ledger.domain.LedgerSourceType;
import com.payflow.ledger.domain.LedgerTransactionEntity;
import com.payflow.ledger.persistence.LedgerAccountRepository;
import com.payflow.ledger.persistence.LedgerEntryRepository;
import com.payflow.ledger.persistence.LedgerTransactionRepository;
import com.payflow.payments.domain.PaymentEntity;
import com.payflow.refunds.domain.RefundEntity;
import com.payflow.shared.error.ApiException;

/**
 * Posts balanced double-entry rows in the caller's PostgreSQL transaction.
 * Propagation is REQUIRED (join only) — never {@code REQUIRES_NEW}.
 */
@Service
public class LedgerPostingService {

	private final LedgerAccountRepository accountRepository;
	private final LedgerTransactionRepository transactionRepository;
	private final LedgerEntryRepository entryRepository;
	private final ObjectProvider<LedgerPostingFaultInjector> faultInjector;

	public LedgerPostingService(LedgerAccountRepository accountRepository,
			LedgerTransactionRepository transactionRepository,
			LedgerEntryRepository entryRepository,
			ObjectProvider<LedgerPostingFaultInjector> faultInjector) {
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
		this.entryRepository = entryRepository;
		this.faultInjector = faultInjector;
	}

	@Transactional
	public void postPaymentCapture(PaymentEntity payment) {
		maybeInjectFault();
		post(
				LedgerSourceType.PAYMENT_CAPTURE,
				payment.getId(),
				payment.getMerchantId(),
				payment.getCurrency(),
				payment.getAmountMinor(),
				LedgerEntryDirection.DEBIT,
				LedgerAccountType.PROCESSOR_CLEARING,
				LedgerEntryDirection.CREDIT,
				LedgerAccountType.MERCHANT_PAYABLE);
	}

	@Transactional
	public void postSuccessfulRefund(RefundEntity refund) {
		maybeInjectFault();
		post(
				LedgerSourceType.REFUND,
				refund.getId(),
				refund.getMerchantId(),
				refund.getCurrency(),
				refund.getAmountMinor(),
				LedgerEntryDirection.DEBIT,
				LedgerAccountType.MERCHANT_PAYABLE,
				LedgerEntryDirection.CREDIT,
				LedgerAccountType.PROCESSOR_CLEARING);
	}

	private void post(
			LedgerSourceType sourceType,
			UUID sourceId,
			UUID merchantId,
			String currency,
			long amountMinor,
			LedgerEntryDirection firstDirection,
			LedgerAccountType firstAccountType,
			LedgerEntryDirection secondDirection,
			LedgerAccountType secondAccountType) {
		if (amountMinor <= 0) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ledger_imbalance",
					"Ledger amount must be a positive minor-unit integer");
		}
		if (!"INR".equals(currency)) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ledger_imbalance",
					"Ledger postings require INR currency");
		}
		if (transactionRepository.existsBySourceTypeAndSourceId(sourceType, sourceId)) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ledger_duplicate",
					"Ledger transaction already exists for this source");
		}

		LedgerAccountEntity firstAccount = requireAccount(firstAccountType, merchantId, currency);
		LedgerAccountEntity secondAccount = requireAccount(secondAccountType, merchantId, currency);

		Instant now = Instant.now();
		UUID transactionId = UUID.randomUUID();
		LedgerTransactionEntity transaction = new LedgerTransactionEntity(
				transactionId, sourceType, sourceId, merchantId, currency, now);

		try {
			transactionRepository.saveAndFlush(transaction);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ledger_duplicate",
					"Ledger transaction already exists for this source");
		}

		entryRepository.saveAndFlush(new LedgerEntryEntity(
				UUID.randomUUID(), transactionId, firstAccount.getId(), firstDirection, amountMinor, now));
		entryRepository.saveAndFlush(new LedgerEntryEntity(
				UUID.randomUUID(), transactionId, secondAccount.getId(), secondDirection, amountMinor, now));

		assertBalanced(transactionId, amountMinor);
	}

	private void assertBalanced(UUID transactionId, long expectedAmountMinor) {
		long debits = entryRepository.sumAmountMinorByTransactionIdAndDirection(
				transactionId, LedgerEntryDirection.DEBIT);
		long credits = entryRepository.sumAmountMinorByTransactionIdAndDirection(
				transactionId, LedgerEntryDirection.CREDIT);
		if (debits != credits || debits != expectedAmountMinor) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ledger_imbalance",
					"Ledger transaction is not balanced");
		}
	}

	private LedgerAccountEntity requireAccount(LedgerAccountType type, UUID merchantId, String currency) {
		return switch (type) {
			case PROCESSOR_CLEARING -> requirePlatformClearing(currency);
			case MERCHANT_PAYABLE -> requireMerchantPayable(merchantId, currency);
		};
	}

	private LedgerAccountEntity requirePlatformClearing(String currency) {
		return accountRepository
				.findByMerchantIdIsNullAndAccountTypeAndCurrency(LedgerAccountType.PROCESSOR_CLEARING, currency)
				.orElseGet(() -> createPlatformClearing(currency));
	}

	private LedgerAccountEntity requireMerchantPayable(UUID merchantId, String currency) {
		return accountRepository
				.findByMerchantIdAndAccountTypeAndCurrency(merchantId, LedgerAccountType.MERCHANT_PAYABLE, currency)
				.orElseGet(() -> createMerchantPayable(merchantId, currency));
	}

	private LedgerAccountEntity createPlatformClearing(String currency) {
		try {
			return accountRepository.saveAndFlush(new LedgerAccountEntity(
					UUID.randomUUID(),
					null,
					LedgerAccountType.PROCESSOR_CLEARING,
					currency,
					true,
					Instant.now()));
		}
		catch (DataIntegrityViolationException ex) {
			return accountRepository
					.findByMerchantIdIsNullAndAccountTypeAndCurrency(LedgerAccountType.PROCESSOR_CLEARING, currency)
					.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
							"Failed to resolve processor clearing account"));
		}
	}

	private LedgerAccountEntity createMerchantPayable(UUID merchantId, String currency) {
		try {
			return accountRepository.saveAndFlush(new LedgerAccountEntity(
					UUID.randomUUID(),
					merchantId,
					LedgerAccountType.MERCHANT_PAYABLE,
					currency,
					true,
					Instant.now()));
		}
		catch (DataIntegrityViolationException ex) {
			return accountRepository
					.findByMerchantIdAndAccountTypeAndCurrency(merchantId, LedgerAccountType.MERCHANT_PAYABLE, currency)
					.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
							"Failed to resolve merchant payable account"));
		}
	}

	private void maybeInjectFault() {
		LedgerPostingFaultInjector injector = faultInjector.getIfAvailable();
		if (injector != null) {
			injector.beforePost();
		}
	}
}
