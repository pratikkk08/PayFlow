/**
 * Merchant analytics read models (Phase 6).
 *
 * <p>Reporting timezone is UTC. Money aggregates use integer minor units only.
 * Metrics are sandbox activity summaries — not settled bank balances or real revenue.
 * Payment status counts reflect current status for payments created in the window;
 * amount and daily event metrics use transition timestamps ({@code captured_at},
 * refund {@code completed_at}, risk {@code evaluated_at}). Historical status paths
 * are not reconstructable from current status alone.
 */
package com.payflow.analytics;
