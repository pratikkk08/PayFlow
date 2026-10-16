package com.payflow.outbox.service;

/**
 * Optional test hook for proving domain rollback when outbox insert fails.
 * Production profile does not register an implementation.
 */
public interface OutboxEventWriteFaultInjector {

	void beforeWrite();
}
