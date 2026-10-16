/**
 * Refund lifecycle against captured payments.
 *
 * <p><strong>Capacity invariant:</strong> for a given payment, the sum of
 * {@code amount_minor} across refunds in {@code PENDING} or {@code SUCCEEDED}
 * must never exceed the payment's captured {@code amount_minor}. {@code PENDING}
 * refunds reserve refundable capacity until they resolve to {@code SUCCEEDED}
 * or {@code FAILED} (which releases the reservation). Enforcement belongs in
 * transactional application logic (later Phase 4 tasks), not payment status —
 * payments remain {@code CAPTURED} when partially or fully refunded.
 *
 * <p>Schema foundation ships in Task 4.1; controllers, processors, ledger, and
 * idempotency follow in later tasks.
 */
package com.payflow.refunds;
