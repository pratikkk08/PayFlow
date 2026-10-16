package com.payflow.outbox;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.payflow.outbox.service.OutboxEventWriteFaultInjector;
import com.payflow.shared.error.ApiException;

@Component
@Profile("test")
class TestOutboxEventWriteFaultInjector implements OutboxEventWriteFaultInjector {

	private final AtomicBoolean failNext = new AtomicBoolean(false);

	void failNextWrite() {
		failNext.set(true);
	}

	@Override
	public void beforeWrite() {
		if (failNext.compareAndSet(true, false)) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "outbox_write_failed",
					"Simulated outbox write failure");
		}
	}
}
