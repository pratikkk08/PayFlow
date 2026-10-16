package com.payflow.ledger;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.payflow.ledger.service.LedgerPostingFaultInjector;
import com.payflow.shared.error.ApiException;

@Component
@Profile("test")
class TestLedgerPostingFaultInjector implements LedgerPostingFaultInjector {

	private final AtomicBoolean failNext = new AtomicBoolean(false);

	void failNextPost() {
		failNext.set(true);
	}

	@Override
	public void beforePost() {
		if (failNext.compareAndSet(true, false)) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ledger_posting_failed",
					"Simulated ledger posting failure");
		}
	}
}
