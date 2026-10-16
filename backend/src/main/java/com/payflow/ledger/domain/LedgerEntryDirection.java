package com.payflow.ledger.domain;

/**
 * Double-entry direction. Amounts are always positive minor units; sign is
 * expressed by direction, never by negative values.
 */
public enum LedgerEntryDirection {
	DEBIT,
	CREDIT
}
