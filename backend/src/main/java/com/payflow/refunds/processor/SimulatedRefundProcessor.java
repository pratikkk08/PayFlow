package com.payflow.refunds.processor;

import com.payflow.payments.domain.PaymentEntity;

/**
 * Port for simulated refund processors. Implementations must be local and synchronous —
 * never call live gateways or move real funds.
 */
public interface SimulatedRefundProcessor {

	RefundResult process(PaymentEntity payment, long amountMinor, String merchantReference);
}
