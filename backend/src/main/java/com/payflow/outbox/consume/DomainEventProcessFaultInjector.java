package com.payflow.outbox.consume;

/**
 * Optional test hook invoked inside the consumer processing transaction.
 * Production has no bean; tests may register one to simulate DB/handler failures.
 */
public interface DomainEventProcessFaultInjector {

	void beforeProcess();
}
