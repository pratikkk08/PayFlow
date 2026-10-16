/**
 * Simulated fraud-risk rules evaluation (Phase 6 — complete).
 *
 * <p>Schema, deterministic scoring, authorize integration, velocity concurrency, and
 * audit/security hardening are implemented. This is <b>not</b> a production-certified
 * fraud detection system. Risk rule-management APIs and manual review approval are not
 * implemented; REVIEW holds a payment in {@code CREATED} without an approval workflow.
 */
package com.payflow.risk;
