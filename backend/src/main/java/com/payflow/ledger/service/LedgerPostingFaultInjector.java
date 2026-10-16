package com.payflow.ledger.service;

/**
 * Optional test hook for proving capture/refund rollback when ledger posting fails.
 * Production profile does not register an implementation.
 */
public interface LedgerPostingFaultInjector {

	void beforePost();
}
