package com.payflow.payments.processor;

/**
 * Outcome of a simulated authorization attempt. Never carries real card data.
 */
public record AuthorizationResult(boolean approved, String processorRef, String failureCode) {

	public static AuthorizationResult approved(String processorRef) {
		return new AuthorizationResult(true, processorRef, null);
	}

	public static AuthorizationResult declined(String failureCode) {
		return new AuthorizationResult(false, null, failureCode);
	}
}
