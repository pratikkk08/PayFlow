package com.payflow.outbox;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.payflow.outbox.consume.DomainEventProcessFaultInjector;

@Component
@Profile("test")
class TestDomainEventProcessFaultInjector implements DomainEventProcessFaultInjector {

	private final AtomicInteger remainingFailures = new AtomicInteger(0);

	void failNext(int count) {
		remainingFailures.set(count);
	}

	void clear() {
		remainingFailures.set(0);
	}

	@Override
	public void beforeProcess() {
		int left = remainingFailures.get();
		if (left > 0 && remainingFailures.compareAndSet(left, left - 1)) {
			throw new IllegalStateException("injected_domain_event_process_failure");
		}
	}
}
