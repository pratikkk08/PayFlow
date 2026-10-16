package com.payflow.refunds.processor;

import org.springframework.stereotype.Component;

import com.payflow.payments.domain.PaymentEntity;

/**
 * Local deterministic refund outcomes. Default path is success; decline when
 * {@code merchantReference} starts with {@code sim_refund_decline_}.
 */
@Component
public class DeterministicSimulatedRefundProcessor implements SimulatedRefundProcessor {

	public static final String REF_PREFIX_DECLINE = "sim_refund_decline_";
	public static final String FAILURE_SIMULATED_DECLINE = "simulated_refund_decline";

	@Override
	public RefundResult process(PaymentEntity payment, long amountMinor, String merchantReference) {
		if (merchantReference.startsWith(REF_PREFIX_DECLINE)) {
			return RefundResult.declined(FAILURE_SIMULATED_DECLINE);
		}
		return RefundResult.success();
	}
}
