package com.payflow.refunds.processor;

/**
 * Outcome of a simulated refund. Sandbox only — never implies real fund movement.
 */
public record RefundResult(boolean approved, String failureCode) {

	public static RefundResult success() {
		return new RefundResult(true, null);
	}

	public static RefundResult declined(String failureCode) {
		return new RefundResult(false, failureCode);
	}
}
