package com.payflow.ledger.domain;

/**
 * Chart-of-accounts types for the simulated double-entry ledger.
 *
 * <ul>
 *   <li>{@code PROCESSOR_CLEARING} — platform-scoped clearing (asset-like).</li>
 *   <li>{@code MERCHANT_PAYABLE} — merchant-scoped payable (liability-like).</li>
 * </ul>
 */
public enum LedgerAccountType {
	PROCESSOR_CLEARING,
	MERCHANT_PAYABLE
}
