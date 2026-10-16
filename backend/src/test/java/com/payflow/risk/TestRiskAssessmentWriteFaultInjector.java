package com.payflow.risk;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.payflow.risk.service.RiskAssessmentWriteFaultInjector;
import com.payflow.shared.error.ApiException;

@Component
@Profile("test")
public class TestRiskAssessmentWriteFaultInjector implements RiskAssessmentWriteFaultInjector {

	private final AtomicBoolean failNext = new AtomicBoolean(false);

	public void failNextPersist() {
		failNext.set(true);
	}

	@Override
	public void beforePersist() {
		if (failNext.compareAndSet(true, false)) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "risk_assessment_write_failed",
					"Simulated risk assessment persistence failure");
		}
	}
}
