# PayFlow — Product Requirements

**Status:** Phase 0 baseline (not yet implemented)  
**Last updated:** 2026-10-12

## 1. Vision

PayFlow is a portfolio demonstration of a **payment-platform-shaped** backend and dashboard. Merchants obtain accounts and API keys, create simulated charges, issue refunds, receive signed webhooks, inspect analytics, and apply simple fraud-risk rules — all without touching real money or card networks.

The product must read as production-*inspired* engineering: clear domains, money-safe design, idempotency, auditability, and reliable asynchronous processing.

## 2. Goals

1. Demonstrate a modular monolith suitable for fintech-style workloads.
2. Provide a credible **simulated** payment lifecycle (authorize, capture, void, settle-like success/failure paths, refunds).
3. Enforce API authentication via hashed API keys (and session/JWT for the dashboard).
4. Guarantee **idempotent** payment and refund creation under retries.
5. Persist financial state in PostgreSQL with an explicit transaction state machine.
6. Deliver **signed** webhooks with retries and delivery logs.
7. Introduce Kafka-backed domain events with **at-least-once**, idempotent consumers.
8. Offer merchant-facing analytics summaries and configurable risk rules.
9. Ship documentation and tests that a hiring reviewer can trust.
10. Keep the entire system runnable locally via Docker Compose on a 16 GB Windows laptop.

## 3. Non-goals

| Non-goal | Rationale |
|----------|-----------|
| Real card processing / acquirers / settlement | Legal, PCI, and cost scope; simulation is sufficient for portfolio value |
| Storing PAN, CVV, or track data | PCI DSS; use tokens or last4 + brand mock fields only |
| Multi-region active-active finance | Overkill for portfolio; design for single-region correctness first |
| Microservices from day one | Operational complexity without proportional learning value early |
| Exactly-once Kafka semantics | Not guaranteed by Kafka alone; design for idempotent consumers |
| Production SaaS billing / multi-tenant isolation at hyperscale | Demonstrate tenant scoping, not hyperscale tenancy platforms |
| Mobile native apps | Web dashboard is enough for MVP |
| Real KYC/AML integrations | Mock onboarding checks only |

## 4. Personas

| Persona | Needs |
|---------|--------|
| Merchant developer | API keys, docs, idempotent charge/refund APIs, webhooks |
| Merchant operator | Dashboard for transactions, refunds, webhook status, basic analytics |
| Platform admin (local/dev) | Seed data, health checks, logs for debugging |
| Hiring reviewer | Clear architecture docs, tests, honest scope |

## 5. MVP requirements (must-have)

MVP is reached when Phases 1–5 acceptance criteria in [ROADMAP.md](ROADMAP.md) are met. Summary:

### 5.1 Identity & merchants

- User registration / login for dashboard access (password hashed).
- Merchant account creation linked to a user.
- API key issuance: show secret **once**, store only a one-way hash (plus prefix for lookup).
- Ability to revoke API keys.

### 5.2 Payments (simulated)

- Create payment (charge) with amount in **minor units**, currency code (ISO 4217), idempotency key.
- State machine: e.g. `CREATED` → `AUTHORIZED` / `FAILED` → `CAPTURED` / `VOIDED` → terminal states as designed in architecture.
- Simulated processor outcomes: success, soft decline, hard decline, timeout/retryable failure (configurable).
- Concurrent duplicate requests with the same idempotency key return the original result.

### 5.3 Refunds

- Partial and full refunds against captured payments, within remaining refundable amount.
- Idempotent refund creation.
- State machine for refund lifecycle.

### 5.4 Ledger / audit

- Append-only or explicitly versioned ledger entries for money movements (or equivalent auditable posting model).
- No silent balance mutation without a recorded entry.

### 5.5 Webhooks

- Merchant configures endpoint URL(s).
- Signed payloads (HMAC) with timestamp.
- Delivery attempts with backoff; persisted delivery status.
- Events at least for payment success/failure and refund completion (exact catalog in architecture).

### 5.6 Platform quality

- Flyway migrations for schema.
- OpenAPI documentation for public merchant APIs.
- Docker Compose for PostgreSQL, Redis, Kafka (and app later).
- Automated backend tests for domain-critical paths (state machine, idempotency, money).

### 5.7 Frontend (MVP stretch vs core)

- Minimal React dashboard: login, API key management view, transaction list, refund action, webhook config — may land in a dedicated phase after core APIs are stable. See roadmap for sequencing.

## 6. Future / advanced features (post-MVP)

- Rich analytics (time-series, cohorts, export)
- Advanced fraud rules engine (velocity, device, allow/deny lists)
- Outbox + Kafka fully production-shaped with DLQ and replay tooling
- Multi-currency FX simulation
- Role-based access control beyond merchant owner
- Playwright end-to-end suites
- Cloud deployment (container registry, managed Postgres/Redis/Kafka)
- Metrics (Prometheus/OpenTelemetry) and tracing
- Dispute / chargeback simulation
- Checkout drop-in UI (still sandbox)

## 7. Functional requirements (selected)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-01 | Merchant can create an API key and authenticate HTTP APIs with it | MVP |
| FR-02 | Payment amounts use integer minor units + currency | MVP |
| FR-03 | Payment status changes only via defined transitions | MVP |
| FR-04 | Idempotency-Key header (or equivalent) deduplicates creates | MVP |
| FR-05 | Refunds cannot exceed captured − already refunded | MVP |
| FR-06 | Webhooks are signed and retryable | MVP |
| FR-07 | Risk rules can block or flag simulated payments | Post-MVP / Phase 6 |
| FR-08 | Analytics endpoints summarize volume/success rates | Post-MVP / Phase 6 |
| FR-09 | Dashboard operators can browse transactions | Phase 7 |

## 8. Non-functional requirements

| ID | Requirement |
|----|-------------|
| NFR-01 | Local stack usable on 16 GB RAM with constrained Docker resources |
| NFR-02 | Structured application logs (JSON or key=value) for backend |
| NFR-03 | Actuator health endpoints for app and dependencies (when wired) |
| NFR-04 | Secrets never logged or committed |
| NFR-05 | Tests run in CI once GitHub Actions is introduced |
| NFR-06 | API responses are stable and versionable (`/api/v1/...`) |

## 9. Payment lifecycle (product view)

See [ARCHITECTURE.md](ARCHITECTURE.md) for the engineering state machine. From a product perspective:

1. Merchant sends create payment (amount, currency, optional customer reference, idempotency key).
2. Platform validates merchant, risk (when enabled), and persists `CREATED`.
3. Simulated processor returns authorize/capture outcome (sync MVP; async enrichment later).
4. Merchant receives API response; webhook fires asynchronously.
5. Merchant may void (if authorized and not captured) or capture (if two-step model enabled).
6. Merchant may refund captured amount partially or fully.
7. Failures (validation, risk block, processor decline, insufficient refundable balance) return explicit error codes.

### Failure scenarios (product)

- Invalid API key / revoked key
- Duplicate idempotency key with different payload → conflict
- Amount ≤ 0 or unsupported currency
- Risk rule block
- Simulated decline (insufficient funds, stolen card mock, etc.)
- Simulated timeout → retryable; client must rely on idempotency
- Refund over remaining balance
- Illegal state transition (e.g. refund before capture)

## 10. Success metrics (portfolio)

- Reviewer can run the stack locally from docs.
- Critical domain tests pass without flakiness under normal conditions.
- Architecture docs match the code.
- No secrets in Git history for intentional commits.

## 11. Out of scope for Phase 0

Phase 0 delivers requirements and scaffolding only. No payment APIs, UI, or Compose stacks are claimed as complete until later phases record them in [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md).
