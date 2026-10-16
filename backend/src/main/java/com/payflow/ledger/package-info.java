/**
 * Double-entry ledger for simulated money movements.
 *
 * <p><strong>Balance invariant:</strong> for every posted {@code ledger_transactions}
 * row, the sum of {@code DEBIT} entry {@code amount_minor} values must equal the sum
 * of {@code CREDIT} entry amounts (same parent currency). Row-level SQL CHECK
 * constraints cannot enforce this multi-row aggregate; posting services must insert
 * the transaction and balanced entries in one DB transaction and verify equality
 * before commit.
 *
 * <p><strong>Conventions (INR minor units only):</strong>
 * <ul>
 *   <li>Capture: debit {@code PROCESSOR_CLEARING}, credit {@code MERCHANT_PAYABLE}.</li>
 *   <li>Successful refund: debit {@code MERCHANT_PAYABLE}, credit {@code PROCESSOR_CLEARING}.</li>
 * </ul>
 *
 * <p>Entries are append-only. Schema foundation ships in Task 4.5; posting logic follows.
 */
package com.payflow.ledger;
