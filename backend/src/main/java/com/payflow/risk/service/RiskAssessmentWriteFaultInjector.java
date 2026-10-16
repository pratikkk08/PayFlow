package com.payflow.risk.service;

/**
 * Optional test hook to simulate assessment persistence failure inside the authorize TX.
 */
public interface RiskAssessmentWriteFaultInjector {

	void beforePersist();
}
