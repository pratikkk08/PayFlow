package com.payflow.payments.processor;

import org.springframework.stereotype.Component;

import com.payflow.payments.domain.PaymentEntity;

/**
 * Local deterministic authorize outcomes for tests and demos.
 *
 * <ul>
 *   <li>{@code merchantReference} starting with {@code sim_decline_} → FAILED
 *       {@code simulated_decline}</li>
 *   <li>{@code merchantReference} starting with {@code sim_insufficient_} → FAILED
 *       {@code insufficient_funds}</li>
 *   <li>{@code amountMinor == 1301} → FAILED {@code insufficient_funds}</li>
 *   <li>otherwise → AUTHORIZED with a synthetic {@code processorRef}</li>
 * </ul>
 */
@Component
public class DeterministicSimulatedPaymentProcessor implements SimulatedPaymentProcessor {

	public static final String REF_PREFIX_DECLINE = "sim_decline_";
	public static final String REF_PREFIX_INSUFFICIENT = "sim_insufficient_";
	public static final long AMOUNT_INSUFFICIENT_FUNDS = 1301L;

	public static final String FAILURE_SIMULATED_DECLINE = "simulated_decline";
	public static final String FAILURE_INSUFFICIENT_FUNDS = "insufficient_funds";

	@Override
	public AuthorizationResult authorize(PaymentEntity payment) {
		String reference = payment.getMerchantReference();
		if (reference.startsWith(REF_PREFIX_DECLINE)) {
			return AuthorizationResult.declined(FAILURE_SIMULATED_DECLINE);
		}
		if (reference.startsWith(REF_PREFIX_INSUFFICIENT) || payment.getAmountMinor() == AMOUNT_INSUFFICIENT_FUNDS) {
			return AuthorizationResult.declined(FAILURE_INSUFFICIENT_FUNDS);
		}
		String processorRef = "sim_auth_" + payment.getId().toString().replace("-", "").substring(0, 12);
		return AuthorizationResult.approved(processorRef);
	}
}
