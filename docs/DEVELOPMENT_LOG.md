# PayFlow — Development Log

Chronological record of meaningful work. Newest entries at the top.

---

## 2026-10-17 — Phase 7: React merchant dashboard & secure browser auth

### Done

- Branch `feature/phase-7-frontend` from Phase 6 (`f943e87`).
- Vite 7 + React 19 + TypeScript + Tailwind 4 SPA: dashboard shell, auth, merchants, analytics, payments/refunds, API keys, webhooks.
- Browser session: memory access JWT, HttpOnly refresh cookie, CSRF on browser login/refresh/logout; concurrent refresh single-flight; merchant-scoped query cache isolation.
- Backend browser-auth endpoints + CORS for Vite `5173`/`5174`; Vite proxy `/api/v1` (avoids swallowing SPA `/api-keys`).
- Secret UX: one-time API-key and webhook signing-secret reveal; not persisted in storage/query cache; OS clipboard caveat documented.
- Verification: frontend **82** tests + typecheck/lint/build; `npm audit --omit=dev` **0** vulnerabilities; backend `clean verify` **211** tests; live local smoke (register/login path, merchants, analytics, payments/refunds, keys, webhooks).

### Not done

- CI, Playwright suite, risk rule-management UI/APIs, webhook secret rotation, access JWT denylist

### Next

Phase 8: CI, hardening, portfolio polish.

---

## 2026-10-16 — Phase 6: Risk rules & merchant analytics

### Done

- Branch `feature/phase-6-risk-analytics` from Phase 5 tip (`5aa4251`).
- Flyway V17–V19: risk rules/assessments/matches, authorization attempts, analytics indexes; `schema_phase = phase-6`.
- Deterministic risk evaluation (AMOUNT_THRESHOLD, PAYMENT_VELOCITY, REPEATED_FAILURES) with FLAG/REVIEW/BLOCK scoring and ALLOW default.
- Authorize integration: payment→merchant locks, attempt record, assessment snapshots, payment/outbox in one TX; REVIEW hold; BLOCK → FAILED once.
- Velocity concurrency-safe via merchant lock; injected Clock; audit/security hardening; fail-closed malformed BLOCK/REVIEW params.
- Merchant analytics overview/daily (JWT OWNER, UTC half-open ranges, integer minor units, event timestamps).
- Hardening: EXPLAIN ANALYZE on ~1.2k-row fixture (sub-ms aggregates; V19 indexes used for narrow windows).
- Tests: **206 passed**, 0 failed via `.\mvnw.cmd clean verify`.

### Not done

- React UI, risk rule-management APIs, manual review approval, CI, webhook secret rotation, HTTP admin retry API

### Next

Phase 7: React dashboard against local API.

---

## 2026-10-16 — Phase 5: Outbox, Kafka, and signed webhooks

### Done

- Branch `feature/phase-5-events-webhooks` from Phase 4 tip (`f28b507`).
- Flyway V12–V16: `outbox_events` and claim tokens, `processed_events`, `webhook_endpoints`, `webhook_deliveries`. Hibernate `ddl-auto=validate`.
- Outbox rows commit in the same transaction as payment/refund changes. Publisher waits for Kafka ACK (`acks=all`) on `payflow.domain-events.v1`. Claim tokens and expired leases keep concurrent publishers from finalizing each other's rows.
- Consumer group `payflow-domain-events-internal` dedupes on `processed_events` and enqueues webhook jobs in that same transaction. Malformed and exhausted events go to `payflow.domain-events.v1.dlt`.
- Webhook registration with AES-256-GCM secrets returned once. Signed delivery uses HMAC `v1`, pinned-IP TLS (SNI, PKIX, original `Host`), redirect rejection, and backoff. Delivery and Kafka stay off unless enabled.
- Ops gauges/counters/timers and `GET /api/v1/internal/events/health` require JWT `ADMIN`. Registration does not grant ADMIN. FAILED requeue is internal and audited by id.
- Local TLS fixture tests cover chain, SNI, hostname, pin, redirects, and read/overall timeouts. A blackhole connect-timeout off-box was not run.
- Tests: **155 passed**, 0 failed via `.\mvnw.cmd clean verify` (Testcontainers Postgres/Redis/Kafka).

### Not done

- Risk rules, analytics, React UI, CI, webhook secret rotation, HTTP admin retry API

### Next

Phase 6: configurable risk checks and merchant analytics summaries.

---

## 2026-10-16 — Phase 4: Refunds & double-entry ledger

### Done

- Branched `feature/phase-4-refunds-ledger` from Phase 3 tip (`281f2a4`).
- Flyway `V8`–`V11`: refunds, refund idempotency, ledger foundation, refund list index; `schema_phase = phase-4`.
- JWT-owner refund APIs: create (required `Idempotency-Key`), get by id, paginated history per payment.
- Capacity invariant: `PENDING` + `SUCCEEDED` refunds never exceed captured amount; payment row lock + concurrent tests.
- Deterministic simulated refund processor; declined refunds release capacity and create no ledger posting.
- Double-entry ledger: capture debit clearing / credit payable; successful refund reverses; unique `(source_type, source_id)`.
- Ledger postings join the payment/refund TX (REQUIRED); fault injection proves rollback of domain + ledger together.
- Tests: **89 passed**, 0 failed via `.\mvnw.cmd clean verify` (Testcontainers Postgres/Redis).
- Docs: ROADMAP Phase 4 criteria, ADR-018/019, ARCHITECTURE/PRODUCT/README/AI_HANDOFF.

### Not done

- Webhooks, outbox, Kafka domain events, risk, analytics, frontend, secret-key payment/refund API

### Next

Phase 5: transactional outbox, Kafka publisher, signed webhook delivery with retries.

---

## 2026-10-13 — Phase 3: Payment processing engine & idempotency

### Done

- Branched `feature/phase-3-payments` from Phase 2 tip.
- Flyway `V3`–`V7`: payments schema, idempotency records, auth/capture/cancel columns, list indexes; `schema_phase = phase-3`.
- JWT-owner payment APIs: create (required `Idempotency-Key`), get, filtered paginated list, authorize, capture, cancel.
- Postgres-backed idempotency (replay + fingerprint conflict); domain state machine; `@Version` + row locks; deterministic simulated processor.
- API keys rejected on payment endpoints; cross-merchant access returns 404.
- Tests: **45 passed** via `.\mvnw.cmd clean verify` (Testcontainers Postgres/Redis).
- Docs: `AI_HANDOFF.md`, ROADMAP Phase 3 criteria, ADR-016/017, ARCHITECTURE/PRODUCT status.

### Not done

- Refunds, ledger, webhooks/outbox, Kafka events, risk, analytics, frontend, secret-key payment API

### Next

Phase 4: refunds within capture limits + ledger postings + concurrency hardening.

---

## 2026-10-13 — Docs: AI development handoff

### Done

- Condensed `AGENTS.md` to durable conventions; pointed agents at `docs/AI_HANDOFF.md`.
- Added `docs/AI_HANDOFF.md` summarizing completed Phases 0–2, Git tip (`46d2f1a` on `feature/phase-2-identity-merchants`), APIs, migrations, run/test commands, and Phase 3 next step.
- No application logic changes.

### Next

Phase 3: simulated payments state machine + idempotency (new branch from Phase 2 tip).

---

## 2026-10-12 — Phase 2: Identity, merchants & secure API keys

### Done

- Branched `feature/phase-2-identity-merchants` from Phase 1 (`75e8b55`).
- Added Flyway `V2__identity_merchants_api_keys.sql` (users, roles, refresh sessions, merchants, memberships, api_keys, audit).
- Implemented register/login, RS256 JWT access tokens, hashed refresh rotation/reuse detection/logout.
- Implemented merchant CRUD (owner-scoped), API key create/list/revoke, integration diagnostic auth.
- Distinguishes JWT vs `sk_test_` Bearer credentials; publishable keys rejected for auth.
- Redis rate limiting on auth endpoints (fail-closed); OpenAPI/springdoc 3.1.1; JSON error model.
- Tests: **12 passed** via `.\mvnw.cmd clean verify`.
- Live E2E against Compose: register → login → me → merchant → API key → integration → revoke → reject; no secret in logs.

### Dependencies added

- `spring-boot-starter-oauth2-resource-server` (Boot managed)
- `springdoc-openapi-starter-webmvc-ui` **3.1.1**

### Not done

- Payments, refunds, email verification, ADMIN workflows, frontend, Kafka domain events

### Next

Phase 3: payment state machine & simulated processor with idempotency.

---

## 2026-10-12 — Phase 1: Backend foundation & local infrastructure

### Done

- Spring Boot **4.1.1** / Java 21 Maven project, Compose Postgres/Redis/optional Kafka, Flyway V1, ping + security deny-by-default.
- Commit `75e8b55` on `feature/phase-1-foundation`.

---

## 2026-10-12 — Phase 0: Initialization

### Done

- Docs, AGENTS.md, scaffolding, environment report. Commit `feb8bba` on `main`.
