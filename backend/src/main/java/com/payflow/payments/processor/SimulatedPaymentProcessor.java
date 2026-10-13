package com.payflow.payments.processor;

import com.payflow.payments.domain.PaymentEntity;

/**
 * Port for simulated authorize/capture processors. Implementations must be
 * deterministic sandbox stubs — never call live gateways or handle PAN/CVV.
 */
public interface SimulatedPaymentProcessor {

	AuthorizationResult authorize(PaymentEntity payment);
}
